package mongo.hkd

import mongo.hkd.macros.{DerivationMacros, NamingResolver}
import reactivemongo.api.bson.{BSONDocumentReader, BSONDocumentWriter}

object deriving {

  class Fields[Data[f[_]]](naming: String => String = identity) {
    implicit val `NamingResolver[Data]` : NamingResolver[Data] = NamingResolver(naming)
    implicit def `BSONField.Fields[Data]`(implicit resolver: NamingResolver[Data]): BSONField.Fields[Data] =
      macro DerivationMacros.fieldsImplWithNamingResolver[Data]
  }

  def fields[Data[f[_]]](naming: String => String = identity): BSONField.Fields[Data] =
    macro DerivationMacros.fieldsImpl[Data]

  trait Reader[Data[f[_]], F[_]] {
    implicit def `BSONReader[Data]`(implicit fields: BSONField.Fields[Data]): BSONDocumentReader[Data[F]] =
      macro DerivationMacros.readerImpl[Data, F]
  }

  def reader[Data[f[_]], F[_]](implicit fields: BSONField.Fields[Data]): BSONDocumentReader[Data[F]] =
    macro DerivationMacros.readerImpl[Data, F]

  trait Writer[Data[f[_]], F[_]] {
    implicit def `BSONWriter[Data]`(implicit fields: BSONField.Fields[Data]): BSONDocumentWriter[Data[F]] =
      macro DerivationMacros.writerImpl[Data, F]
  }

  def writer[Data[f[_]], F[_]](implicit fields: BSONField.Fields[Data]): BSONDocumentWriter[Data[F]] =
    macro DerivationMacros.writerImpl[Data, F]

}
