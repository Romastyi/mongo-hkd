package mongo.hkd

import reactivemongo.api.{DB, FailoverStrategy}

trait CollectionDsl {

  implicit class MongoDBOps(private val db: DB) {
    def collectionOf[Data[f[_]]](name: String, failoverStrategy: FailoverStrategy = db.failoverStrategy)(implicit
        fields: BSONField.Fields[Data]
    ): HKDBSONCollection[Data] =
      HKDBSONCollection(db.collection(name, failoverStrategy))
  }

}
