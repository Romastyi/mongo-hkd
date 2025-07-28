package mongo.hkd

import reactivemongo.api.bson.*

import scala.util.Try

private[hkd] class DerivedBSONReader[Data[f[_]], F[_]](
    override val fields: BSONField.Fields[Data],
    readers: Iterable[BSONReader[?]],
    ctor: Seq[Any] => Data[F]
) extends BSONDocumentReader[Data[F]] with DerivedBSONInstance[Data, F] {
  override def readDocument(bson: BSONDocument): Try[Data[F]] =
    (productIter(fields) zip readers)
      .foldLeft(Try(Seq.empty[Any])) { case (acc, (f, r)) =>
        acc.flatMap(prev =>
          r.asInstanceOf[BSONReader[Any]]
            .readTry(bson.get(f.asInstanceOf[BSONField[Any]].fieldName).getOrElse(BSONNull))
            .map(v => prev :+ v)
        )
      }
      .map(ctor)
}

private[hkd] class DerivedBSONWriter[Data[f[_]], F[_]](
    override val fields: BSONField.Fields[Data],
    writers: Iterable[BSONWriter[?]]
) extends BSONDocumentWriter[Data[F]] with DerivedBSONInstance[Data, F] {
  override def writeTry(data: Data[F]): Try[BSONDocument] =
    (productIter(data) zip productIter(fields) zip writers)
      .foldLeft(Try(Seq.empty[(String, BSONValue)])) { case (acc, ((v, f), w)) =>
        acc.flatMap(prev =>
          w.asInstanceOf[BSONWriter[Any]]
            .writeTry(v)
            .map(bson => prev :+ (f.asInstanceOf[BSONField[Any]].fieldName -> bson))
        )
      }
      .map(BSONDocument.apply)
}

private[hkd] trait DerivedBSONInstance[Data[f[_]], F[_]] {
  def fields: BSONField.Fields[Data]

  def productIter(p: Any): Iterator[Any] = p.asInstanceOf[Product].productIterator
}
