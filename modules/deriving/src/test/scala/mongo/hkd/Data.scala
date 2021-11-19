package mongo.hkd

import mongo.hkd.implicits._
import reactivemongo.api.bson._

import java.util.UUID

final case class Data[F[_]](
    id: F[Int],
    name: F[String],
    description: F[Option[String]],
    isActive: F[Boolean],
    tags: F[List[String]],
    nestedData: F[NestedData[F]],
    otherData: F[Option[List[NestedData[F]]]]
)

object Data
    extends deriving.Fields[Data](renaming.snakeCase) with deriving.Writer[Data, Ident]
    with deriving.Reader[Data, Ident] {
  implicit val optReader: BSONDocumentReader[Data[Option]] = deriving.reader[Data, Option]
  implicit val optWriter: BSONDocumentWriter[Data[Option]] = deriving.writer[Data, Option]
}

final case class NestedData[F[_]](
    id: F[UUID],
    firstField: F[Option[Long]],
    secondField: F[Option[String]]
)

object NestedData
    extends deriving.Fields[NestedData](renaming.snakeCase) with deriving.Writer[NestedData, Ident]
    with deriving.Reader[NestedData, Ident] {
  implicit val optReader: BSONDocumentReader[NestedData[Option]] = deriving.reader[NestedData, Option]
  implicit val optWriter: BSONDocumentWriter[NestedData[Option]] = deriving.writer[NestedData, Option]
}
