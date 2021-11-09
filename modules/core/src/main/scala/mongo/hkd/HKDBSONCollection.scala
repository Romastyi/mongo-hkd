package mongo.hkd

import reactivemongo.api.bson.collection.BSONCollection

trait HKDBSONCollection[Data[f[_]]] {
  def fields: Record.RecordFields[Data]
  def delegate[A](f: BSONCollection => A): A
}

object HKDBSONCollection {

  private case class HKDBSONCollectionImpl[Data[f[_]]](
      private val underlying: BSONCollection,
      override val fields: Record.RecordFields[Data]
  ) extends HKDBSONCollection[Data] {
    override def delegate[A](f: BSONCollection => A): A = f(underlying)
  }

  def apply[Data[f[_]]](collection: BSONCollection)(implicit fields: BSONField.Fields[Data]): HKDBSONCollection[Data] =
    HKDBSONCollectionImpl(collection, Record.asRecord[Data, BSONField](fields))

}
