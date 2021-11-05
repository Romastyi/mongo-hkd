package mongo.hkd

import reactivemongo.api.bson._

import scala.util.Try

final case class oid() extends scala.annotation.StaticAnnotation

trait BSONField[A] {
  def fieldName: String
}

object BSONField extends HighPriorityImplicits {

  type Fields[Data[f[_]]] = Data[BSONField]

  private case class Impl[A](override val fieldName: String)           extends BSONField[A]
  private case class Nested[A](baseName: String, nested: BSONField[A]) extends BSONField[A] {
    override val fieldName: String = s"""$baseName.${nested.fieldName}"""
  }

  def apply[A](fieldName: String): BSONField[A] = Impl(fieldName)

  def fields[Data[f[_]]](implicit inst: Fields[Data]): Fields[Data] = inst

  implicit class BSONFieldSyntax[Data[f[_]]](private val data: BSONField[Data[BSONField]]) extends AnyVal {
    def nested[A](accessor: Data[BSONField] => BSONField[A])(implicit f: Fields[Data]): BSONField[A] =
      Nested(data.fieldName, accessor(f))

    @inline def ~[A](accessor: Data[BSONField] => BSONField[A])(implicit f: Fields[Data]): BSONField[A] = nested(
      accessor
    )
  }

}

trait HighPriorityImplicits extends LowPriorityImplicits {

  implicit class BSONFieldHKDCodecs[Data[f[_]]](field: BSONField[Data[BSONField]]) {
    def read[F[_]](bson: BSONDocument)(implicit r: BSONReader[F[Data[F]]]): Try[F[Data[F]]]          =
      bson.getAsTry[F[Data[F]]](field.fieldName)
    def write[F[_]](value: F[Data[F]])(implicit w: BSONWriter[F[Data[F]]]): Try[(String, BSONValue)] =
      w.writeTry(value).map(field.fieldName -> _)
  }

  implicit class BSONFieldOptionCodecs[A](field: BSONField[Option[A]]) {
    def read[F[_]](bson: BSONDocument)(implicit r: BSONReader[F[Option[A]]]): Try[F[Option[A]]] =
      r.readTry(bson.get(field.fieldName).getOrElse(BSONNull))
  }

}

trait LowPriorityImplicits {

  implicit class BSONFieldCodecs[A](field: BSONField[A]) {
    def read[F[_]](bson: BSONDocument)(implicit r: BSONReader[F[A]]): Try[F[A]]          =
      bson.getAsTry[F[A]](field.fieldName)
    def write[F[_]](value: F[A])(implicit w: BSONWriter[F[A]]): Try[(String, BSONValue)] =
      w.writeTry(value).map(field.fieldName -> _)
  }

}
