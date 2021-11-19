package mongo.hkd

import reactivemongo.api.bson._

import scala.util.Try

trait BSONField[A] {
  def fieldName: String
}

object BSONField extends NestedHKDCodes with DefaultCodecs {

  type Fields[Data[f[_]]] = Data[BSONField]

  private case class Impl[A](override val fieldName: String)                extends BSONField[A]
  private case class Nested[A, B](base: BSONField[A], nested: BSONField[B]) extends BSONField[B] {
    override val fieldName: String = s"""${base.fieldName}.${nested.fieldName}"""
  }

  def apply[A](fieldName: String): BSONField[A] = Impl(fieldName)

  def fields[Data[f[_]]](implicit inst: Fields[Data]): Fields[Data] = inst

  implicit class BSONFieldSyntax[T, Data[f[_]]](field: BSONField[T])(implicit
      w: DerivedFieldType.Nested[T, Data],
      fields: BSONField.Fields[Data]
  ) {
    def nested[A](accessor: Fields[Data] => BSONField[A]): BSONField[A] =
      Nested(field, accessor(fields))

    @inline def ~[A](accessor: Fields[Data] => BSONField[A]): BSONField[A] = nested(accessor)
  }

}

trait NestedHKDCodes {

  implicit class BSONFieldHKDCodecs5[A, G[_], H[_], I[_], J[_], Data[f[_]]](
      field: BSONField[G[H[I[J[Data[BSONField]]]]]]
  ) {
    type M[B] = G[H[I[J[B]]]]
    def read[F[_]](bson: BSONDocument)(implicit r: BSONReader[F[M[Data[F]]]]): Try[F[M[Data[F]]]]          =
      r.readTry(bson.get(field.fieldName).getOrElse(BSONNull))
    def write[F[_]](value: F[M[Data[F]]])(implicit w: BSONWriter[F[M[Data[F]]]]): Try[(String, BSONValue)] =
      w.writeTry(value).map(field.fieldName -> _)
  }

  implicit class BSONFieldHKDCodecs4[G[_], H[_], I[_], Data[f[_]]](field: BSONField[G[H[I[Data[BSONField]]]]]) {
    type M[B] = G[H[I[B]]]
    def read[F[_]](bson: BSONDocument)(implicit r: BSONReader[F[M[Data[F]]]]): Try[F[M[Data[F]]]]          =
      r.readTry(bson.get(field.fieldName).getOrElse(BSONNull))
    def write[F[_]](value: F[M[Data[F]]])(implicit w: BSONWriter[F[M[Data[F]]]]): Try[(String, BSONValue)] =
      w.writeTry(value).map(field.fieldName -> _)
  }

  implicit class BSONFieldHKDCodecs3[G[_], H[_], Data[f[_]]](field: BSONField[G[H[Data[BSONField]]]]) {
    type M[A] = G[H[A]]
    def read[F[_]](bson: BSONDocument)(implicit r: BSONReader[F[M[Data[F]]]]): Try[F[M[Data[F]]]]          =
      r.readTry(bson.get(field.fieldName).getOrElse(BSONNull))
    def write[F[_]](value: F[M[Data[F]]])(implicit w: BSONWriter[F[M[Data[F]]]]): Try[(String, BSONValue)] =
      w.writeTry(value).map(field.fieldName -> _)
  }

  implicit class BSONFieldHKDCodecs2[G[_], Data[f[_]]](field: BSONField[G[Data[BSONField]]]) {
    def read[F[_]](bson: BSONDocument)(implicit r: BSONReader[F[G[Data[F]]]]): Try[F[G[Data[F]]]]          =
      r.readTry(bson.get(field.fieldName).getOrElse(BSONNull))
    def write[F[_]](value: F[G[Data[F]]])(implicit w: BSONWriter[F[G[Data[F]]]]): Try[(String, BSONValue)] =
      w.writeTry(value).map(field.fieldName -> _)
  }

  implicit class BSONFieldHKDCodecs1[Data[f[_]]](field: BSONField[Data[BSONField]]) {
    def read[F[_]](bson: BSONDocument)(implicit r: BSONReader[F[Data[F]]]): Try[F[Data[F]]]          =
      r.readTry(bson.get(field.fieldName).getOrElse(BSONNull))
    def write[F[_]](value: F[Data[F]])(implicit w: BSONWriter[F[Data[F]]]): Try[(String, BSONValue)] =
      w.writeTry(value).map(field.fieldName -> _)
  }

}

trait DefaultCodecs {

  implicit class BSONFieldCodecs[A](field: BSONField[A]) {
    def read[F[_]](bson: BSONDocument)(implicit r: BSONReader[F[A]]): Try[F[A]]          =
      r.readTry(bson.get(field.fieldName).getOrElse(BSONNull))
    def write[F[_]](value: F[A])(implicit w: BSONWriter[F[A]]): Try[(String, BSONValue)] =
      w.writeTry(value).map(field.fieldName -> _)
  }

}
