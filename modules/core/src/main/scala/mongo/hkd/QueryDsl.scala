package mongo.hkd

import reactivemongo.api.Cursor.WithOps
import reactivemongo.api.ReadPreference
import reactivemongo.api.bson.*
import reactivemongo.api.bson.collection.BSONCollection

import scala.concurrent.{ExecutionContext, Future}

final class QueryOperations[Data[f[_]]](
    private val builder: BSONCollection#QueryBuilder,
    private val fields: Record.RecordFields[Data]
) {
  private def options(f: BSONCollection#QueryBuilder => BSONCollection#QueryBuilder): QueryOperations[Data] =
    new QueryOperations(f(builder), fields)

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

sealed trait Query {
  def bson: BSONDocument
}

object Query {
  implicit def `BSONWriter[Query]`[Q <: Query]: BSONDocumentWriter[Q] = BSONDocumentWriter(_.bson)
}

final case class LogicalOperator[A <: Query, B <: Query](left: A, right: B, operator: String) extends Query {
  override def bson: BSONDocument = document(operator -> array(left.bson, right.bson))
}

final case class FieldMatchExpr[A](field: BSONField[A], value: BSONValueWrapper) extends Query {
  def elem: ElementProducer       = field.fieldName -> value.bson
  override def bson: BSONDocument = document(elem)
}

sealed trait QueryExpr[A] extends Query {
  def field: BSONField[A]
  def expr: BSONDocument
  final override def bson: BSONDocument = document(field.fieldName -> expr)
}

final case class FieldComparison[A](override val field: BSONField[A], operator: String, value: BSONValueWrapper)
    extends QueryExpr[A] {
  override def expr: BSONDocument = document(operator -> value.bson)
}

final case class ElemMatch[A](override val field: BSONField[A], query: Query) extends QueryExpr[A] {
  override def expr: BSONDocument = document("$elemMatch" -> query.bson)
}

final case class FieldComparisonOperators[A, V](field: BSONField[A])(implicit w: BSONWriter[V]) {
  infix def $eq(value: V): FieldComparison[A]                                  = FieldComparison(field, "$eq", value)
  infix def $ne(value: V): FieldComparison[A]                                  = FieldComparison(field, "$ne", value)
  infix def $gt(value: V): FieldComparison[A]                                  = FieldComparison(field, "$gt", value)
  infix def $gte(value: V): FieldComparison[A]                                 = FieldComparison(field, "$gte", value)
  infix def $lt(value: V): FieldComparison[A]                                  = FieldComparison(field, "$lt", value)
  infix def $lte(value: V): FieldComparison[A]                                 = FieldComparison(field, "$lte", value)
  infix def $in(value: V, others: V*): FieldComparison[A]                      = FieldComparison(field, "$in", value +: others)
  infix def $nin(value: V, others: V*): FieldComparison[A]                     = FieldComparison(field, "$nin", value +: others)
  infix def $not(expr: BSONField[A] => FieldComparison[A]): FieldComparison[A] =
    FieldComparison(field, "$not", expr(field).expr)
}

sealed trait QueryProjection
final case class FieldIncluded[A](field: BSONField[A])                              extends QueryProjection
final case class FieldExcluded[A](field: BSONField[A])                              extends QueryProjection
final case class FieldArraySlice[A](field: BSONField[A], n: Int, skip: Option[Int]) extends QueryProjection
final case class FieldArrayElemMatch[A](elemMatch: ElemMatch[A])                    extends QueryProjection

object QueryProjection {
  implicit val `BSONWriter[QueryProjection]` : BSONDocumentWriter[Seq[QueryProjection]] = BSONDocumentWriter { xs =>
    document(xs.map {
      case FieldIncluded(field)                  => BSONElement(field.fieldName, 1)
      case FieldExcluded(field)                  => BSONElement(field.fieldName, 0)
      case FieldArraySlice(field, n, None)       => BSONElement(field.fieldName, document("$slice" -> n))
      case FieldArraySlice(field, n, Some(skip)) => BSONElement(field.fieldName, document("$slice" -> List(n, skip)))
      case FieldArrayElemMatch(elemMatch)        => elemMatch.bson
    }*)
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
    }*)
  }
}

trait QueryDsl extends QueryDslLowPriorityImplicits with Compat {

  implicit class CollectionQueryOperations[Data[f[_]]](collection: HKDBSONCollection[Data]) {
    def findAll: QueryOperations[Data]                                              =
      new QueryOperations(collection.delegate(_.find(document)), collection.fields)
    def findQuery(query: Record.RecordFields[Data] => Query): QueryOperations[Data] =
      new QueryOperations(collection.delegate(_.find(query(collection.fields))), collection.fields)
  }

  implicit class LogicalOperators[A <: Query](left: A) {
    infix def $and[B <: Query](right: B): LogicalOperator[A, B] = LogicalOperator(left, right, "$and")
    infix def $nor[B <: Query](right: B): LogicalOperator[A, B] = LogicalOperator(left, right, "$nor")
    infix def $or[B <: Query](right: B): LogicalOperator[A, B]  = LogicalOperator(left, right, "$or")
  }

  implicit class NestedFieldMatches[A, Data[f[_]]](field: BSONField[A])(implicit
      f: DerivedFieldType.Nested[A, Data]
  ) {
    infix def m[F[_]](value: Data[F])(implicit w: BSONWriter[Data[F]]): FieldMatchExpr[A] =
      FieldMatchExpr(field, w.writeTry(value).get)
  }

  implicit def stringComparisons(field: BSONField[String]): StringComparisons[({ type id[x] = x })#id] =
    new StringComparisons[({ type id[x] = x })#id](field)

  implicit class StringComparisons[F[_]](field: BSONField[F[String]]) {
    infix def $regex(regex: String, flags: String = ""): FieldComparison[F[String]] =
      FieldComparison(field, f"""$$regex""", BSONRegex(regex, flags))
  }

  implicit def arrayComparisons[A, T](field: BSONField[A])(implicit
      f: DerivedFieldType.Array[A, T],
      w: BSONWriter[T]
  ): FieldComparisonOperators[A, T] = FieldComparisonOperators(field)

  implicit class ArrayFieldNestedDocumentQueryOps[A, Data[f[_]]](field: BSONField[A])(implicit
      w: DerivedFieldType.Array[A, Data[BSONField]],
      fields: BSONField.Fields[Data]
  ) {
    infix def $all(
        elem: BSONField[A] => ElemMatch[A],
        elems: (BSONField[A] => ElemMatch[A])*
    ): FieldComparison[A]                                                      =
      FieldComparison(field, "$all", (elem +: elems).map(_.apply(field)).map(_.expr))
    infix def $size(size: Int): FieldComparison[A]                             = FieldComparison(field, f"$$size", size)
    infix def $elemMatch(query: BSONField.Fields[Data] => Query): ElemMatch[A] = ElemMatch(field, query(fields))
  }

}

trait QueryDslLowPriorityImplicits {

  implicit def foldFieldMatches(xs: Seq[FieldMatchExpr[?]]): Query = new Query {
    override def bson: BSONDocument = document(xs.map(_.elem)*)
  }

  implicit class FieldMatches[A](field: BSONField[A])(implicit w: BSONWriter[A]) {
    infix def m(value: A): FieldMatchExpr[A] = FieldMatchExpr(field, value)
  }

  implicit def comparisons[A](field: BSONField[A])(implicit w: BSONWriter[A]): FieldComparisonOperators[A, A] =
    FieldComparisonOperators(field)

  sealed trait IncludedField
  val incl: IncludedField                                                                                          =
    new IncludedField {}
  implicit def includedField[A](p: (BSONField[A], IncludedField)): QueryProjection                                 =
    FieldIncluded(p._1)
  implicit def includedPositionalArrayField[A, T](p: (PositionalArrayField[A, T], IncludedField)): QueryProjection =
    FieldIncluded(p._1.field)

  sealed trait ExcludedField
  val excl: ExcludedField                                                          = new ExcludedField {}
  implicit def excludedField[A](p: (BSONField[A], ExcludedField)): QueryProjection = FieldExcluded(p._1)

  implicit class FieldSimpleProjects[A](field: BSONField[A]) {
    def asc: QuerySort  = FieldAscending(field)
    def desc: QuerySort = FieldDescending(field)
  }

  implicit class ArrayFieldsOperators[A, T](field: BSONField[A])(implicit f: DerivedFieldType.Array[A, T]) {
    // Query
    infix def $all(value: T, others: T*)(implicit w: BSONWriter[T]): FieldComparison[A] =
      FieldComparison(field, "$all", value +: others)
    infix def $size(size: Int): FieldComparison[A]                                      =
      FieldComparison(field, f"$$size", size)

    // Projection
    infix def slice(n: Int): QueryProjection            = FieldArraySlice(field, n, None)
    infix def slice(n: Int, skip: Int): QueryProjection = FieldArraySlice(field, n, Some(skip))
  }

  implicit def elemMatchProjection[A](elemMatch: ElemMatch[A]): QueryProjection =
    FieldArrayElemMatch(elemMatch)

}
