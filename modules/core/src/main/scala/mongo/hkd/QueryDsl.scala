package mongo.hkd

import reactivemongo.api.Cursor.WithOps
import reactivemongo.api.ReadPreference
import reactivemongo.api.bson._
import reactivemongo.api.bson.collection.BSONCollection

import scala.annotation.nowarn
import scala.collection.Factory
import scala.concurrent.{ExecutionContext, Future}

sealed trait BSONValueWrapper {
  def bson: BSONValue
}

object BSONValueWrapper {
  implicit def wrapper[A](value: A)(implicit w: BSONWriter[A]): BSONValueWrapper = new BSONValueWrapper {
    override def bson: BSONValue = w.writeTry(value).get
  }
}

sealed trait Query {
  def bson: BSONDocument
}

object Query {
  implicit def `BSONWriter[Query]`[Q <: Query]: BSONDocumentWriter[Q] = BSONDocumentWriter(_.bson)
}

final case class LogicalOperator[A <: Query, B <: Query](left: A, right: B, operator: String) extends Query {
  override def bson: BSONDocument = document(operator -> array(left.bson, right.bson))
}

final case class FieldMatchExpr[A](field: BSONField[A], wrapper: BSONValueWrapper) extends Query {
  def elem: ElementProducer       = field.fieldName -> wrapper.bson
  override def bson: BSONDocument = document(elem)
}

sealed trait QueryExpr[A] extends Query {
  def field: BSONField[A]
  def expr: BSONDocument
  final override def bson: BSONDocument = document(field.fieldName -> expr)
}

final case class FieldComparison[A](override val field: BSONField[A], operator: String, wrapper: BSONValueWrapper)
    extends QueryExpr[A] {
  override def expr: BSONDocument = document(operator -> wrapper.bson)
}

final case class ElemMatch[Data[f[_]], M[_]](override val field: BSONField[M[Data[BSONField]]], query: Query)
    extends QueryExpr[M[Data[BSONField]]] {
  override def expr: BSONDocument = document("$elemMatch" -> query.bson)
}

final case class FieldComparisonOperators[A, V](field: BSONField[A])(implicit w: BSONWriter[V]) {
  def $eq(value: V): FieldComparison[A]                                  = FieldComparison(field, "$eq", value)
  def $ne(value: V): FieldComparison[A]                                  = FieldComparison(field, "$ne", value)
  def $gt(value: V): FieldComparison[A]                                  = FieldComparison(field, "$gt", value)
  def $gte(value: V): FieldComparison[A]                                 = FieldComparison(field, "$gte", value)
  def $lt(value: V): FieldComparison[A]                                  = FieldComparison(field, "$lt", value)
  def $lte(value: V): FieldComparison[A]                                 = FieldComparison(field, "$lte", value)
  def $in(value: V, others: V*): FieldComparison[A]                      = FieldComparison(field, "$in", value +: others)
  def $nin(value: V, others: V*): FieldComparison[A]                     = FieldComparison(field, "$nin", value +: others)
  def $not(expr: BSONField[A] => FieldComparison[A]): FieldComparison[A] =
    FieldComparison(field, "$not", expr(field).expr)
}

final case class QueryOperations[Data[f[_]]](
    private val builder: BSONCollection#QueryBuilder,
    private val fields: Record.RecordFields[Data]
) {
  private def options(f: BSONCollection#QueryBuilder => BSONCollection#QueryBuilder): QueryOperations[Data] =
    QueryOperations(f(builder), fields)

  def projection(projections: (Record.RecordFields[Data] => QueryProjection)*): QueryOperations[Data] =
    options(_.projection(projections.map(_.apply(fields))))

  def sort(soring: (Record.RecordFields[Data] => QuerySort)*): QueryOperations[Data] =
    options(_.sort(BSON.writeDocument(soring.map(_.apply(fields))).get))

  def one[F[_]](implicit
      ec: ExecutionContext,
      reader: BSONDocumentReader[BSONRecord[Data, F]]
  ): Future[Option[BSONRecord[Data, F]]] =
    builder.one[BSONRecord[Data, F]]

  def one[F[_]](
      readPreference: ReadPreference
  )(implicit
      ec: ExecutionContext,
      reader: BSONDocumentReader[BSONRecord[Data, F]]
  ): Future[Option[BSONRecord[Data, F]]] =
    builder.one[BSONRecord[Data, F]](readPreference)

  def requireOne[F[_]](implicit
      ec: ExecutionContext,
      reader: BSONDocumentReader[BSONRecord[Data, F]]
  ): Future[BSONRecord[Data, F]] =
    builder.requireOne[BSONRecord[Data, F]]

  def requireOne[F[_]](
      readPreference: ReadPreference
  )(implicit
      ec: ExecutionContext,
      reader: BSONDocumentReader[BSONRecord[Data, F]]
  ): Future[BSONRecord[Data, F]] =
    builder.requireOne[BSONRecord[Data, F]](readPreference)

  def cursor[F[_]](implicit reader: BSONDocumentReader[BSONRecord[Data, F]]): WithOps[BSONRecord[Data, F]] =
    builder.cursor[BSONRecord[Data, F]]()

  def cursor[F[_]](readPreference: ReadPreference)(implicit
      reader: BSONDocumentReader[BSONRecord[Data, F]]
  ): WithOps[BSONRecord[Data, F]] =
    builder.cursor[BSONRecord[Data, F]](readPreference)
}

sealed trait QueryProjection
final case class FieldIncluded[A](field: BSONField[A])                                extends QueryProjection
final case class FieldExcluded[A](field: BSONField[A])                                extends QueryProjection
final case class FieldArraySlice[A](field: BSONField[A], n: Int, skip: Option[Int])   extends QueryProjection
final case class FieldArrayElemMatch[Data[f[_]], M[_]](elemMatch: ElemMatch[Data, M]) extends QueryProjection

object QueryProjection {
  implicit val `BSONWriter[QueryProjection]` : BSONDocumentWriter[Seq[QueryProjection]] = BSONDocumentWriter { xs =>
    document(xs.map {
      case FieldIncluded(field)                  => BSONElement(field.fieldName, 1)
      case FieldExcluded(field)                  => BSONElement(field.fieldName, 0)
      case FieldArraySlice(field, n, None)       => BSONElement(field.fieldName, document("$slice" -> n))
      case FieldArraySlice(field, n, Some(skip)) => BSONElement(field.fieldName, document("$slice" -> List(n, skip)))
      case FieldArrayElemMatch(elemMatch)        => elemMatch.bson
    }: _*)
  }
}

sealed trait QuerySort
final case class FieldAscending[A](field: BSONField[A])  extends QuerySort
final case class FieldDescending[A](field: BSONField[A]) extends QuerySort

object QuerySort {
  implicit val `BSONWriter[QuerySort]` : BSONDocumentWriter[Seq[QuerySort]] = BSONDocumentWriter { xs =>
    document(xs.map {
      case FieldAscending(field)  => BSONElement(field.fieldName, 1)
      case FieldDescending(field) => BSONElement(field.fieldName, -1)
    }: _*)
  }
}

@nowarn("msg=is never used")
trait QueryDsl extends QueryDslLowPriorityImplicits {

  implicit class CollectionQueryOperations[Data[f[_]]](collection: HKDBSONCollection[Data]) {
    def findAll: QueryOperations[Data]                                              =
      QueryOperations(collection.delegate(_.find(document)), collection.fields)
    def findQuery(query: Record.RecordFields[Data] => Query): QueryOperations[Data] =
      QueryOperations(collection.delegate(_.find(query(collection.fields))), collection.fields)
  }

  implicit class LogicalOperators[A <: Query](left: A) {
    def $and[B <: Query](right: B): LogicalOperator[A, B] = LogicalOperator(left, right, "$and")
    def $nor[B <: Query](right: B): LogicalOperator[A, B] = LogicalOperator(left, right, "$nor")
    def $or[B <: Query](right: B): LogicalOperator[A, B]  = LogicalOperator(left, right, "$or")
  }

  implicit class NestedFieldMatches[Data[f[_]]](field: BSONField[Data[BSONField]]) {
    def m[F[_]](value: Data[F])(implicit w: BSONWriter[Data[F]]): FieldMatchExpr[Data[BSONField]] =
      FieldMatchExpr(field, w.writeTry(value).get)
  }

  implicit def stringComparisons(field: BSONField[String]): StringComparisons[({ type id[x] = x })#id] =
    new StringComparisons[({ type id[x] = x })#id](field)

  implicit class StringComparisons[F[_]](field: BSONField[F[String]]) {
    def $regex(regex: String, flags: String = ""): FieldComparison[F[String]] =
      FieldComparison(field, f"""$$regex""", BSONRegex(regex, flags))
  }

  implicit def arrayComparisons[A, M[_]](field: BSONField[M[A]])(implicit
      f: Factory[A, M[A]],
      w: BSONWriter[A]
  ): FieldComparisonOperators[M[A], A] = FieldComparisonOperators(field)

  implicit class ArrayFieldNestedDocumentQueryOps[M[_], Data[f[_]]](field: BSONField[M[Data[BSONField]]])(implicit
      fields: BSONField.Fields[Data]
  ) {
    def $all(
        elem: BSONField[M[Data[BSONField]]] => ElemMatch[Data, M],
        elems: (BSONField[M[Data[BSONField]]] => ElemMatch[Data, M])*
    ): FieldComparison[M[Data[BSONField]]]                                     =
      FieldComparison(field, "$all", (elem +: elems).map(_.apply(field)).map(_.expr))
    def $size(size: Int): FieldComparison[M[Data[BSONField]]]                  = FieldComparison(field, f"$$size", size)
    def $elemMatch(query: BSONField.Fields[Data] => Query): ElemMatch[Data, M] = ElemMatch(field, query(fields))
  }

}

@nowarn("msg=is never used")
trait QueryDslLowPriorityImplicits {

  implicit def foldFieldMatches(xs: Seq[FieldMatchExpr[_]]): Query = new Query {
    override def bson: BSONDocument = document(xs.map(_.elem): _*)
  }

  implicit class FieldMatches[A](field: BSONField[A])(implicit w: BSONWriter[A]) {
    def m(value: A): FieldMatchExpr[A] = FieldMatchExpr(field, value)
  }

  implicit def comparisons[A](field: BSONField[A])(implicit w: BSONWriter[A]): FieldComparisonOperators[A, A] =
    FieldComparisonOperators(field)

  implicit class ArrayFieldsQueryOperators[M[_], A](field: BSONField[M[A]])(implicit
      f: Factory[A, M[A]],
      w: BSONWriter[A]
  ) {
    def $all(value: A, others: A*): FieldComparison[M[A]] = FieldComparison(field, "$all", value +: others)
    def $size(size: Int): FieldComparison[M[A]]           = FieldComparison(field, f"$$size", size)
  }

  implicit class FieldSimpleProjects[A](field: BSONField[A]) {
    def ->(w: 1): QueryProjection                  = FieldIncluded(field)
    def ->(w: 0, unit: Unit = ()): QueryProjection = FieldExcluded(field)
    // Sorting
    def asc: QuerySort                             = FieldAscending(field)
    def desc: QuerySort                            = FieldDescending(field)
  }

  final class PositionalArrayFieldOps[M[_], A](field: BSONField[M[A]]) {
    def ->(w: 1): QueryProjection = FieldIncluded(field)
  }

  implicit class ArrayFieldSimpleProjections[M[_], A](field: BSONField[M[A]])(implicit f: Factory[A, M[A]]) {
    def $ : PositionalArrayFieldOps[M, A]         = new PositionalArrayFieldOps(BSONField(s"${field.fieldName}.$$"))
    def slice(n: Int): QueryProjection            = FieldArraySlice(field, n, None)
    def slice(n: Int, skip: Int): QueryProjection = FieldArraySlice(field, n, Some(skip))
  }

  implicit def elemMatchProjection[Data[f[_]], M[_]](elemMatch: ElemMatch[Data, M]): QueryProjection =
    FieldArrayElemMatch(elemMatch)

}
