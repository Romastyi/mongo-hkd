package mongo.hkd

import reactivemongo.api.bson._

import scala.reflect.ClassTag
import scala.util.{Success, Try}

object implicits extends CustomCodecs

trait CustomCodecs {

  // Need explicit readers for supported collections to resolve BSONReader[F[M[A]]]
  implicit def readArray[A: ClassTag](implicit r: BSONReader[A]): BSONReader[Array[A]] = collectionReader[Array, A]
  implicit def readSeq[A](implicit r: BSONReader[A]): BSONReader[Seq[A]]               = collectionReader[Seq, A]
  implicit def readSet[A](implicit r: BSONReader[A]): BSONReader[Set[A]]               = collectionReader[Set, A]
  implicit def readList[A](implicit r: BSONReader[A]): BSONReader[List[A]]             = collectionReader[List, A]
  implicit def readVector[A](implicit r: BSONReader[A]): BSONReader[Vector[A]]         = collectionReader[Vector, A]

  implicit def bsonOptionReader[T](implicit r: BSONReader[T]): BSONReader[Option[T]] = {
    case BSONNull => Success(None)
    case bson     => r.readTry(bson).map(Some.apply)
  }
  implicit def bsonOptionWriter[T](implicit w: BSONWriter[T]): BSONWriter[Option[T]] =
    (t: Option[T]) => t.fold[Try[BSONValue]](Success(BSONNull))(w.writeTry)

}
