package mongo.hkd

import com.softwaremill.quicklens._
import mongo.hkd.dsl._
import mongo.hkd.implicits._
import reactivemongo.api.bson._

import java.util.UUID
import scala.util.Random

class UpdateDslTest extends CommonMongoSpec {

  def record(): BSONRecord[Data, Ident]                                     = record(BSONObjectID.generate(), data())
  def record(oid: BSONObjectID, data: Data[Ident]): BSONRecord[Data, Ident] = data.record(oid)

  def data(): Data[Ident] = data(Random.nextInt(), UUID.randomUUID())
  def data(id: Int, uuid: UUID): Data[Ident] = {
    val nestedData = NestedData[Ident](id = uuid, firstField = Some(1), secondField = Some("field"))
    Data[Ident](
      id = id,
      name = "name",
      description = Some("description"),
      isActive = true,
      tags = List("tag1", "tag2"),
      nestedData = nestedData,
      otherData = Some(List(nestedData))
    )
  }

  "Update" - {
    "updateOne" in withCollection[Data].apply { collection =>
      val item = record()
      for {
        _       <- collection.insert[Ident].one(item)
        found   <- collection.findQuery(_.id $eq item.data.id).requireOne[Ident]
        _       <- collection.update.one(
                     _._id $eq item._id,
                     _ $inc (_.id                      -> 2)
                       $set (_.name                    -> "name~")
                       $unset (_.description)
                       $mul (_.nestedData./.firstField -> 2L)
                   )
        updated <- collection.findQuery(_._id $eq item._id).requireOne[Ident]
      } yield {
        found shouldBe item
        updated shouldBe item
          .modify(_.data.id)
          .using(_ + 2)
          .modify(_.data.name)
          .setTo("name~")
          .modify(_.data.description)
          .setTo(None)
          .modify(_.data.nestedData)
          .using(_.modify(_.firstField).using(_.map(_ * 2)))
      }
    }
    "bulk update" in withCollection[Data].apply { collection =>
      val item1 = record()
      val item2 = record()
      for {
        _       <- collection.insert[Ident].many(item1, item2)
        found   <- collection.findQuery(_._id $in (item1._id, item2._id)).cursor[Ident].collect[List]()
        _       <- collection.update.bulk(
                     _.op(_._id $eq item1._id)(_.id $inc 1, _.name $set "name~1"),
                     _.op(_._id $eq item2._id, _.$inc(_.id -> 2).$set(_.name -> "name~2"))
                   )
        updated <- collection.findQuery(_._id $in (item1._id, item2._id)).cursor[Ident].collect[List]()
      } yield {
        found shouldBe List(item1, item2)
        updated shouldBe List(
          item1.modify(_.data.id).using(_ + 1).modify(_.data.name).setTo("name~1"),
          item2.modify(_.data.id).using(_ + 2).modify(_.data.name).setTo("name~2")
        )
      }
    }
  }

}
