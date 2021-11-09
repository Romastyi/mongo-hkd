package mongo.hkd

import reactivemongo.api.bson._

sealed trait Record

object Record {
  private[hkd] val idField: String = "_id"

  type AsRecord[Data[f[_]], F[_]] = Data[F] with Record
  type RecordFields[Data[f[_]]]   = BSONField.Fields[AsRecord[Data, *[_]]]

  def asRecord[Data[f[_]], F[_]](data: Data[F]): AsRecord[Data, F] = data.asInstanceOf[AsRecord[Data, F]]

  implicit class RecordOps[Data[f[_]]](private val fields: RecordFields[Data]) extends AnyVal {
    def _id: BSONField[BSONObjectID] = BSONField(idField)
  }
}

final case class BSONRecord[Data[f[_]], F[_]](_id: F[BSONObjectID], data: Data[F]) extends Record

object BSONRecord {
  implicit def reader[Data[f[_]], F[_]](implicit
      readId: BSONReader[F[BSONObjectID]],
      readData: BSONDocumentReader[Data[F]]
  ): BSONDocumentReader[BSONRecord[Data, F]] = BSONDocumentReader.from(bson =>
    for {
      _id  <- bson.getAsTry[F[BSONObjectID]](Record.idField)
      data <- readData.readTry(bson)
    } yield BSONRecord(_id, data)
  )
  implicit def writer[Data[f[_]], F[_]](implicit
      writeId: BSONWriter[F[BSONObjectID]],
      writeData: BSONDocumentWriter[Data[F]]
  ): BSONDocumentWriter[BSONRecord[Data, F]] = BSONDocumentWriter.from { case BSONRecord(_id, data) =>
    for {
      bsonId   <- writeId.writeTry(_id).map(Record.idField -> _)
      bsonData <- writeData.writeTry(data)
    } yield document(bsonId) ++ bsonData
  }
}
