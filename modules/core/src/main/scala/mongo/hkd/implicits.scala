package mongo.hkd

import reactivemongo.api.bson.{BSONNull, BSONReader, BSONValue, BSONWriter}

import scala.util.{Success, Try}

object implicits extends CustomCodecs

trait CustomCodecs {

  implicit def bsonOptionReader[T](implicit r: BSONReader[T]): BSONReader[Option[T]] = {
    case BSONNull => Success(None)
    case bson     => r.readTry(bson).map(Some.apply)
  }
  implicit def bsonOptionWriter[T](implicit w: BSONWriter[T]): BSONWriter[Option[T]] =
    (t: Option[T]) => t.fold[Try[BSONValue]](Success(BSONNull))(w.writeTry)

}
