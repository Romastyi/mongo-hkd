package mongo.hkd

import reactivemongo.api.bson.*

import scala.compiletime.*
import scala.deriving.*
import scala.annotation.nowarn

@nowarn
object deriving {

  private inline def summonFields[T <: Tuple](naming: String => String): List[BSONField[?]] =
    inline erasedValue[T] match {
      case _: EmptyTuple => Nil
      case _: (t *: ts)  => BSONField(naming(constValue[t].asInstanceOf[String])) :: summonFields[ts](naming)
    }

  class Fields[Data[f[_]] <: Product](naming: String => String = identity) {
    inline given `BSONField.Fields[Data]` : BSONField.Fields[Data] = fields[Data](naming)
  }

  inline def fields[Data[f[_]] <: Product](naming: String => String = identity): BSONField.Fields[Data] = summonFrom {
    case p: Mirror.ProductOf[Data[BSONField]] =>
      val pack = summonFields[p.MirroredElemLabels](naming)
      p.fromProduct(new {
        def productArity           = pack.length
        def canEqual(that: Any)    = true
        def productElement(i: Int) = pack(i)
      })
  }

  private def productIter(a: Any): Iterator[Any] = a.asInstanceOf[Product].productIterator

  private[hkd] inline def nested[A, Data[f[_]]](
      field: BSONField[A],
      nested: BSONField.Fields[Data]
  ): BSONField.Fields[Data] = summonFrom { case p: Mirror.ProductOf[Data[BSONField]] =>
    val pack = productIter(nested).map(f => BSONField.Nested(field, f.asInstanceOf[BSONField[Any]])).toSeq
    p.fromProduct(new {
      def productArity           = pack.length
      def canEqual(that: Any)    = true
      def productElement(i: Int) = pack(i)
    })
  }

  trait Reader[Data[f[_]], F[_]] {
    inline given (using fields: BSONField.Fields[Data]): BSONDocumentReader[Data[F]] = reader[Data, F]
  }

  private inline def summonReaders[T <: Tuple]: List[BSONReader[?]] = inline erasedValue[T] match {
    case _: EmptyTuple => Nil
    case _: (t *: ts)  => summonInline[BSONReader[t]] :: summonReaders[ts]
  }

  inline def reader[Data[f[_]], F[_]](using fields: BSONField.Fields[Data]): BSONDocumentReader[Data[F]] = summonFrom {
    case p: Mirror.ProductOf[Data[F]] =>
      val readers = summonReaders[p.MirroredElemTypes]
      new DerivedBSONReader[Data, F](
        fields,
        readers,
        { pack =>
          p.fromProduct(new {
            def productArity           = pack.length
            def canEqual(that: Any)    = true
            def productElement(i: Int) = pack(i)
          })
        }
      )
  }

  trait Writer[Data[f[_]], F[_]] {
    inline given (using fields: BSONField.Fields[Data]): BSONDocumentWriter[Data[F]] = writer[Data, F]
  }

  private inline def summonWriters[T <: Tuple]: List[BSONWriter[?]] = inline erasedValue[T] match {
    case _: EmptyTuple => Nil
    case _: (t *: ts)  => summonInline[BSONWriter[t]] :: summonWriters[ts]
  }

  inline def writer[Data[f[_]], F[_]](using fields: BSONField.Fields[Data]): BSONDocumentWriter[Data[F]] = summonFrom {
    case p: Mirror.ProductOf[Data[F]] =>
      val writers = summonWriters[p.MirroredElemTypes]
      new DerivedBSONWriter[Data, F](fields, writers)
  }

}
