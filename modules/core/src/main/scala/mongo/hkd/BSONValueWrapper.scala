package mongo.hkd

import reactivemongo.api.bson.{BSONValue, BSONWriter}

sealed trait BSONValueWrapper {
  def bson: BSONValue
}

object BSONValueWrapper {
  implicit def instance[A](value: A)(implicit w: BSONWriter[A]): BSONValueWrapper = new BSONValueWrapper {
    override def bson: BSONValue = w.writeTry(value).get
  }
}
