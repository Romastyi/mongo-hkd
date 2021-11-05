package mongo.hkd

import mongo.hkd.implicits._
import reactivemongo.api.bson._

import java.util.UUID

final case class Data[F[_]](
    @oid oid: F[String],
    id: F[Int],
    name: F[String],
    description: F[Option[String]],
    isActive: F[Boolean],
    nestedData: F[NestedData[F]]
)

object Data
    extends deriving.Fields[Data](renaming.snakeCase) with deriving.Writer[Data, Id] with deriving.Reader[Data, Id] {
  implicit val optReader: BSONDocumentReader[Data[Option]] = deriving.reader[Data, Option]
  implicit val optWriter: BSONDocumentWriter[Data[Option]] = deriving.writer[Data, Option]
}

final case class NestedData[F[_]](
    id: F[UUID],
    secondField: F[Option[String]]
)

object NestedData
    extends deriving.Fields[NestedData](renaming.snakeCase) with deriving.Writer[NestedData, Id]
    with deriving.Reader[NestedData, Id] {
  implicit val optReader: BSONDocumentReader[NestedData[Option]] = deriving.reader[NestedData, Option]
  implicit val optWriter: BSONDocumentWriter[NestedData[Option]] = deriving.writer[NestedData, Option]
}
