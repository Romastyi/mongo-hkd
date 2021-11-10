package mongo.hkd

import reactivemongo.api.bson._

import scala.collection.Factory
import scala.reflect.{ClassTag, classTag}
import scala.util.Try

trait BSONField[A] {
  def fieldName: String
}

object BSONField extends HighPriorityImplicits {

  type Fields[Data[f[_]]] = Data[BSONField]

  private case class Impl[A](override val fieldName: String)                extends BSONField[A]
  private case class Nested[A, B](base: BSONField[A], nested: BSONField[B]) extends BSONField[B] {
    override val fieldName: String = s"""${base.fieldName}.${nested.fieldName}"""
  }

  def apply[A](fieldName: String): BSONField[A] = Impl(fieldName)

  def fields[Data[f[_]]](implicit inst: Fields[Data]): Fields[Data] = inst

  implicit class ArrayFieldSyntax[Data[f[_]], M[_]](field: BSONField[M[Data[BSONField]]])(implicit
      f: Factory[Data[BSONField], M[Data[BSONField]]]
  ) {
    def ~[A](accessor: Fields[Data] => BSONField[A])(implicit f: Fields[Data]): BSONField[A] =
      Nested(field, accessor(f))
  }

  implicit class BSONFieldSyntax[Data[f[_]]](val field: BSONField[Data[BSONField]]) extends AnyVal {
    def nested[A](accessor: Fields[Data] => BSONField[A])(implicit f: Fields[Data]): BSONField[A] =
      Nested(field, accessor(f))

    @inline def ~[A](accessor: Fields[Data] => BSONField[A])(implicit f: Fields[Data]): BSONField[A] = nested(
      accessor
    )
  }

}

trait HighPriorityImplicits extends LowPriorityImplicits {

  implicit class BSONFieldHKDCollectionCodecs[Data[f[_]], M[_]](field: BSONField[M[Data[BSONField]]])(implicit
      f: Factory[Data[BSONField], M[Data[BSONField]]]
  ) {
    def read[F[_]](
        bson: BSONDocument
    )(implicit r: BSONReader[F[M[Data[F]]]], ct: ClassTag[F[M[Data[F]]]]): Try[F[M[Data[F]]]]              =
      readOptional[F[M[Data[F]]]](field, bson)
    def write[F[_]](value: F[M[Data[F]]])(implicit w: BSONWriter[F[M[Data[F]]]]): Try[(String, BSONValue)] =
      w.writeTry(value).map(field.fieldName -> _)
  }

  implicit class BSONFieldHKDCodecs[Data[f[_]]](field: BSONField[Data[BSONField]]) {
    def read[F[_]](bson: BSONDocument)(implicit r: BSONReader[F[Data[F]]], ct: ClassTag[F[Data[F]]]): Try[F[Data[F]]] =
      readOptional[F[Data[F]]](field, bson)
    def write[F[_]](value: F[Data[F]])(implicit w: BSONWriter[F[Data[F]]]): Try[(String, BSONValue)]                  =
      w.writeTry(value).map(field.fieldName -> _)
  }

  implicit class BSONFieldOptionCodecs[A](field: BSONField[Option[A]]) {
    def read[F[_]](bson: BSONDocument)(implicit r: BSONReader[F[Option[A]]]): Try[F[Option[A]]] =
      r.readTry(bson.get(field.fieldName).getOrElse(BSONNull))
  }

}

trait LowPriorityImplicits {

  protected def readOptional[A](field: BSONField[_], bson: BSONDocument)(implicit
      r: BSONReader[A],
      ct: ClassTag[A]
  ): Try[A] = {
    // TODO Make without reflection
    if (ct.runtimeClass.isAssignableFrom(classTag[Option[A]].runtimeClass)) {
      r.readTry(bson.get(field.fieldName).getOrElse(BSONNull))
    } else {
      bson.getAsTry[A](field.fieldName)
    }
  }

  implicit class BSONFieldCodecs[A](field: BSONField[A]) {
    def read[F[_]](bson: BSONDocument)(implicit r: BSONReader[F[A]], ct: ClassTag[F[A]]): Try[F[A]] =
      readOptional[F[A]](field, bson)
    def write[F[_]](value: F[A])(implicit w: BSONWriter[F[A]]): Try[(String, BSONValue)]            =
      w.writeTry(value).map(field.fieldName -> _)
  }

}
