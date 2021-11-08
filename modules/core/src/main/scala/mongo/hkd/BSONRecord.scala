package mongo.hkd

import reactivemongo.api.bson._

final case class BSONRecord[Data[f[_]], F[_]](_id: F[BSONObjectID], data: Data[F])

object BSONRecord {
  private val idField: String                = "_id"
  implicit def reader[Data[f[_]], F[_]](implicit
      readId: BSONReader[F[BSONObjectID]],
      readData: BSONDocumentReader[Data[F]]
  ): BSONDocumentReader[BSONRecord[Data, F]] = BSONDocumentReader.from(bson =>
    for {
      _id  <- bson.getAsTry[F[BSONObjectID]](idField)
      data <- readData.readTry(bson)
    } yield BSONRecord(_id, data)
  )
  implicit def writer[Data[f[_]], F[_]](implicit
      writeId: BSONWriter[F[BSONObjectID]],
      writeData: BSONDocumentWriter[Data[F]]
  ): BSONDocumentWriter[BSONRecord[Data, F]] = BSONDocumentWriter.from { case BSONRecord(_id, data) =>
    for {
      bsonId   <- writeId.writeTry(_id).map(idField -> _)
      bsonData <- writeData.writeTry(data)
    } yield document(bsonId) ++ bsonData
  }
}
