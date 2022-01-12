package mongo.hkd

import reactivemongo.api.bson._

import scala.util.Try

trait BSONField[A] extends BSONFieldCompat[A] {
  def fieldName: String
}

object BSONField extends DefaultCodecs with LowPriorityImplicits {

  type Fields[Data[f[_]]] = Data[BSONField]

  private case class Impl[A](override val fieldName: String)                     extends BSONField[A]
  private[hkd] case class Nested[A, B](base: BSONField[A], nested: BSONField[B]) extends BSONField[B] {
    override val fieldName: String = s"""${base.fieldName}.${nested.fieldName}"""
  }

  def apply[A](fieldName: String): BSONField[A] = Impl(fieldName)

  def fields[Data[f[_]]](implicit inst: Fields[Data]): Fields[Data] = inst

}

trait DefaultCodecs {

  implicit class BSONFieldCodecs[A](field: BSONField[A]) {
    def read[F[_]](bson: BSONDocument)(implicit r: BSONReader[F[A]]): Try[F[A]]          =
      r.readTry(bson.get(field.fieldName).getOrElse(BSONNull))
    def write[F[_]](value: F[A])(implicit w: BSONWriter[F[A]]): Try[(String, BSONValue)] =
      w.writeTry(value).map(field.fieldName -> _)
  }

}

final case class PositionalArrayField[A, T](field: BSONField[A])

trait LowPriorityImplicits {
  implicit class PositionalArrayFieldOps[A, T](field: BSONField[A])(implicit f: DerivedFieldType.Array[A, T]) {
    def $ : PositionalArrayField[A, T] = PositionalArrayField[A, T](BSONField(s"${field.fieldName}.$$"))
  }
}
