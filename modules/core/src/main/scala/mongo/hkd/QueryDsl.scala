package mongo.hkd

import reactivemongo.api.Cursor.WithOps
import reactivemongo.api.ReadPreference
import reactivemongo.api.bson._
import reactivemongo.api.bson.collection.BSONCollection

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

trait QueryDsl extends QueryDslLowPriorityImplicits {

  case class FindOperations[Data[f[_]]](builder: BSONCollection#QueryBuilder) {
    def options(f: BSONCollection#QueryBuilder => BSONCollection#QueryBuilder): FindOperations[Data] =
      FindOperations(f(builder))

    def one[F[_]](implicit ec: ExecutionContext, reader: BSONDocumentReader[Data[F]]): Future[Option[Data[F]]] =
      builder.one[Data[F]]
    def one[F[_]](
        readPreference: ReadPreference
    )(implicit ec: ExecutionContext, reader: BSONDocumentReader[Data[F]]): Future[Option[Data[F]]]             =
      builder.one[Data[F]](readPreference)

    def requireOne[F[_]](implicit ec: ExecutionContext, reader: BSONDocumentReader[Data[F]]): Future[Data[F]] =
      builder.requireOne[Data[F]]
    def requireOne[F[_]](
        readPreference: ReadPreference
    )(implicit ec: ExecutionContext, reader: BSONDocumentReader[Data[F]]): Future[Data[F]]                    =
      builder.requireOne[Data[F]](readPreference)

    def cursor[F[_]](implicit reader: BSONDocumentReader[Data[F]]): WithOps[Data[F]]                                 =
      builder.cursor[Data[F]]()
    def cursor[F[_]](readPreference: ReadPreference)(implicit reader: BSONDocumentReader[Data[F]]): WithOps[Data[F]] =
      builder.cursor[Data[F]](readPreference)
  }

  implicit class CollectionOperations(collection: BSONCollection) {
    def findAll[Data[f[_]]]: FindOperations[Data] =
      FindOperations(collection.find(document))
    def findQuery[Data[f[_]]](query: BSONField.Fields[Data] => Query)(implicit
        fields: BSONField.Fields[Data]
    ): FindOperations[Data]                       =
      FindOperations(collection.find(query(fields).bson))
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

  implicit def stringComparisons(field: BSONField[String]): StringComparisons[Id] =
    new StringComparisons[Id](field)

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
