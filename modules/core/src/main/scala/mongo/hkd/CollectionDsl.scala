package mongo.hkd

import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.bson.{BSON, BSONDocument, BSONDocumentWriter, BSONObjectID}
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.{DB, FailoverStrategy, WriteConcern}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

sealed trait InsertWrapper[Data[f[_]], F[_]] {
  def write: Try[BSONDocument]
}
final case class InsertDataWrapper[Data[f[_]], F[_]](data: Data[F], implicit val writer: BSONDocumentWriter[Data[F]])
    extends InsertWrapper[Data, F]           {
  override def write: Try[BSONDocument] = BSON.writeDocument(data)
}
final case class InsertRecordWrapper[Data[f[_]], F[_]](
    record: BSONRecord[Data, F],
    implicit val writer: BSONDocumentWriter[BSONRecord[Data, F]]
) extends InsertWrapper[Data, F] {
  override def write: Try[BSONDocument] = BSON.writeDocument(record)
}

object InsertWrapper {
  implicit def insertDataWrapper[Data[f[_]], F[_]](data: Data[F])(implicit
      writer: BSONDocumentWriter[Data[F]]
  ): InsertWrapper[Data, F]                                                                              =
    InsertDataWrapper(data, writer)
  implicit def insertRecordWrapper[Data[f[_]], F[_]](record: BSONRecord[Data, F])(implicit
      writer: BSONDocumentWriter[BSONRecord[Data, F]]
  ): InsertWrapper[Data, F]                                                                              =
    InsertRecordWrapper(record, writer)
  implicit def `BSONWriter[InsertWrapper]`[Data[f[_]], F[_]]: BSONDocumentWriter[InsertWrapper[Data, F]] =
    BSONDocumentWriter.from(_.write)
}

final case class InsertOperations[Data[f[_]], F[_]](private val builder: BSONCollection#InsertBuilder) {
  def one(item: InsertWrapper[Data, F])(implicit ec: ExecutionContext): Future[WriteResult] =
    builder.one(item)

  def many(first: InsertWrapper[Data, F], others: InsertWrapper[Data, F]*)(implicit
      ec: ExecutionContext
  ): Future[BSONCollection#MultiBulkWriteResult] =
    builder.many(first +: others)
}

trait CollectionDsl {

  implicit class MongoDBOps(private val db: DB) {
    def collectionOf[Data[f[_]]](name: String, failoverStrategy: FailoverStrategy = db.failoverStrategy)(implicit
        fields: BSONField.Fields[Data]
    ): HKDBSONCollection[Data] =
      HKDBSONCollection(db.collection(name, failoverStrategy))
  }

  implicit class BSONRecordOps[Data[f[_]], F[_]](private val data: Data[F]) {
    def record(_id: F[BSONObjectID]): BSONRecord[Data, F] = BSONRecord(_id, data)
  }

  implicit class CollectionModifyOperations[Data[f[_]]](private val collection: HKDBSONCollection[Data]) {
    def insert[F[_]]: InsertOperations[Data, F]                                                      =
      InsertOperations(collection.delegate(_.insert))
    def insert[F[_]](ordered: Boolean): InsertOperations[Data, F]                                    =
      InsertOperations(collection.delegate(_.insert(ordered)))
    def insert[F[_]](writeConcern: WriteConcern): InsertOperations[Data, F]                          =
      InsertOperations(collection.delegate(_.insert(writeConcern)))
    def insert[F[_]](ordered: Boolean, writeConcern: WriteConcern): InsertOperations[Data, F]        =
      InsertOperations(collection.delegate(_.insert(ordered, writeConcern)))
    def insert[F[_]](ordered: Boolean, bypassDocumentValidation: Boolean): InsertOperations[Data, F] =
      InsertOperations(collection.delegate(_.insert(ordered, bypassDocumentValidation)))
    def insert[F[_]](
        ordered: Boolean,
        writeConcern: WriteConcern,
        bypassDocumentValidation: Boolean
    ): InsertOperations[Data, F]                                                                     =
      InsertOperations(collection.delegate(_.insert(ordered, writeConcern, bypassDocumentValidation)))
  }

}
