package mongo.hkd

import reactivemongo.api.bson._

import scala.annotation.nowarn
import scala.collection.Factory
import scala.util.Try

trait BSONField[A] {
  def fieldName: String
}

object BSONField extends NestedHKDCodes with SimpleFieldCodecs {

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

sealed trait DerivedFieldType[A, T, +E]

@nowarn("msg=is never used")
object DerivedFieldType extends DerivedFieldTypeLowPriorityImplicits {

  sealed trait SimpleField
  sealed trait OptionField
  sealed trait ArrayField

  type Field[A, T]           = DerivedFieldType[A, T, SimpleField]
  type Array[A, T]           = DerivedFieldType[A, T, ArrayField with SimpleField]
  type Option[A, T]          = DerivedFieldType[A, T, OptionField with SimpleField]
  type Nested[A, Data[f[_]]] = DerivedFieldType[A, Data[BSONField], SimpleField]

  def apply[A, T, E](): DerivedFieldType[A, T, E] = null
  def field[A](): Field[A, A]                     = null
  def array[A, T](): Array[A, T]                  = null
  def option[A, T](): Option[A, T]                = null

  implicit def wrappedOption[A, T, E](implicit
      w: DerivedFieldType[A, T, E]
  ): DerivedFieldType[scala.Option[A], T, OptionField with E] = null
  implicit def wrappedArray[A, M[_], T, E](implicit
      w: DerivedFieldType[A, T, E],
      f: Factory[A, M[A]]
  ): DerivedFieldType[M[A], T, ArrayField with E]             = null
  implicit def wrappedNested[Data[f[_]]](implicit
      fs: BSONField.Fields[Data]
  ): DerivedFieldType.Nested[Data[BSONField], Data]           = null
}

@nowarn("msg=is never used")
sealed trait DerivedFieldTypeLowPriorityImplicits {
  implicit def wrappedProduct[A, Repr <: Product](implicit ev: Repr ¬ Option[A]): DerivedFieldType.Field[Repr, Repr] =
    null
  implicit def wrappedAnyVal[A <: AnyVal]: DerivedFieldType.Field[A, A]                                              = null
  implicit def wrappedField[A: BSONWriter]: DerivedFieldType.Field[A, A]                                             = null
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

trait SimpleFieldCodecs {

  implicit class BSONFieldCodecs[A](field: BSONField[A]) {
    def read[F[_]](bson: BSONDocument)(implicit r: BSONReader[F[A]]): Try[F[A]]          =
      r.readTry(bson.get(field.fieldName).getOrElse(BSONNull))
    def write[F[_]](value: F[A])(implicit w: BSONWriter[F[A]]): Try[(String, BSONValue)] =
      w.writeTry(value).map(field.fieldName -> _)
  }

}
