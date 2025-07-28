package mongo.hkd

import reactivemongo.api.bson.BSONObjectID
import reactivemongo.api.bson.collection.BSONCollectionProducer
import reactivemongo.api.{DB, FailoverStrategy}

trait CollectionDsl {

  implicit class MongoDBOps(private val db: DB) {
    def collectionOf[Data[f[_]]](name: String, failoverStrategy: FailoverStrategy = db.failoverStrategy)(implicit
        fields: BSONField.Fields[Data]
    ): HKDBSONCollection[Data] =
      HKDBSONCollection(db.collection(name, failoverStrategy))
  }

  implicit class BSONRecordOps[Data[f[_]]: BSONField.Fields, F[_]](private val data: Data[F]) {
    def record(_id: F[BSONObjectID]): BSONRecord[Data, F] = BSONRecord(_id, data)
  }

}
