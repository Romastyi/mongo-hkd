package mongo.hkd

import reactivemongo.api.Cursor.WithOps
import reactivemongo.api.ReadPreference
import reactivemongo.api.bson._
import reactivemongo.api.bson.collection.BSONCollection

import scala.annotation.nowarn
import scala.concurrent.{ExecutionContext, Future}

sealed trait BSONValueWrapper {
  def bson: BSONValue
}

sealed trait Query {
  def bson: BSONDocument
}

final case class LogicalOperator[A <: Query, B <: Query](left: A, right: B, operator: String) extends Query {
  def bson: BSONDocument = document(operator -> array(left.bson, right.bson))
}

final case class FieldMatchExpr[A](field: BSONField[A], wrapper: BSONValueWrapper) extends Query {
  def elem: ElementProducer = field.fieldName -> wrapper.bson
  def bson: BSONDocument    = document(elem)
}

final case class FieldComparison[A](field: BSONField[A], operator: String, wrapper: BSONValueWrapper) extends Query {
  def expr: BSONDocument = document(operator -> wrapper.bson)
  def bson: BSONDocument = document(field.fieldName -> expr)
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
final case class FieldIncluded[A](field: BSONField[A]) extends QueryProjection
final case class FieldExcluded[A](field: BSONField[A]) extends QueryProjection

object QueryProjection {
  implicit val `BSONWriter[QueryProjection]` : BSONDocumentWriter[Seq[QueryProjection]] = BSONDocumentWriter { xs =>
    document(xs.map {
      case FieldIncluded(field) => BSONElement(field.fieldName, 1)
      case FieldExcluded(field) => BSONElement(field.fieldName, 0)
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

trait QueryDsl extends QueryDslLowPriorityImplicits {

  @nowarn("msg=is never used")
  implicit class FieldOptions[A](private val field: BSONField[A]) {
    def ->[W <: 1](w: W): QueryProjection                  = FieldIncluded(field)
    def ->[W <: 0](w: W, unit: Unit = ()): QueryProjection = FieldExcluded(field)

    def asc: QuerySort  = FieldAscending(field)
    def desc: QuerySort = FieldDescending(field)
  }

  implicit class CollectionQueryOperations[Data[f[_]]](collection: HKDBSONCollection[Data]) {
    def findAll: QueryOperations[Data]                                              =
      QueryOperations(collection.delegate(_.find(document)), collection.fields)
    def findQuery(query: Record.RecordFields[Data] => Query): QueryOperations[Data] =
      QueryOperations(collection.delegate(_.find(query(collection.fields).bson)), collection.fields)
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

  implicit class StringComparisons[F[_]](field: BSONField[F[String]])(implicit w: BSONWriter[F[String]]) {
    def $regex(regex: String, flags: String = ""): FieldComparison[F[String]] =
      FieldComparison(field, f"""$$regex""", BSONRegex(regex, flags))
  }

}

trait QueryDslLowPriorityImplicits {

  implicit def wrapper[A](value: A)(implicit w: BSONWriter[A]): BSONValueWrapper = new BSONValueWrapper {
    override def bson: BSONValue = w.writeTry(value).get
  }

  implicit def foldFieldMatches(xs: Seq[FieldMatchExpr[_]]): Query = new Query {
    override def bson: BSONDocument = document(xs.map(_.elem): _*)
  }

  implicit class FieldMatches[A](field: BSONField[A])(implicit w: BSONWriter[A]) {
    def m(value: A): FieldMatchExpr[A] = FieldMatchExpr(field, value)
  }

  implicit class Comparisons[A](field: BSONField[A])(implicit w: BSONWriter[A]) {
    def $eq(value: A): FieldComparison[A]                                  = FieldComparison(field, "$eq", value)
    def $ne(value: A): FieldComparison[A]                                  = FieldComparison(field, "$ne", value)
    def $gt(value: A): FieldComparison[A]                                  = FieldComparison(field, "$gt", value)
    def $gte(value: A): FieldComparison[A]                                 = FieldComparison(field, "$gte", value)
    def $lt(value: A): FieldComparison[A]                                  = FieldComparison(field, "$lt", value)
    def $lte(value: A): FieldComparison[A]                                 = FieldComparison(field, "$lte", value)
    def $in(value: A, others: A*): FieldComparison[A]                      = FieldComparison(field, "$in", value +: others)
    def $nin(value: A, others: A*): FieldComparison[A]                     = FieldComparison(field, "$nin", value +: others)
    def $not(expr: BSONField[A] => FieldComparison[A]): FieldComparison[A] =
      FieldComparison(field, "$not", expr(field).expr)
  }

}
