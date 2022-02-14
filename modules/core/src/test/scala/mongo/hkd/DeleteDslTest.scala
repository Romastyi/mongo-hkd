package mongo.hkd

import mongo.hkd.dsl._
import mongo.hkd.implicits._
import reactivemongo.api.bson._

import java.util.UUID
import scala.util.Random

class DeleteDslTest extends CommonMongoSpec {

  def record(): BSONRecord[Data, Ident] = record(BSONObjectID.generate(), data())

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

  "Delete" - {
    "deleteOne" in withCollection[Data].apply { collection =>
      val item1 = record()
      val item2 = record()
      val item3 = record()
      val item4 = record()
      for {
        _       <- collection.insert[Ident].many(item1, item2, item3, item4)
        found0  <- collection.findAll.cursor[Ident].collect[List]()
        result0 <- collection.delete.one(_.name $eq "name")
        found1  <- collection.findAll.cursor[Ident].collect[List]()
        result1 <- collection.delete.one(_._id $eq item3._id)
        found2  <- collection.findAll.cursor[Ident].collect[List]()
        result2 <- collection.delete.one(_.name $eq "name")
        found3  <- collection.findAll.cursor[Ident].collect[List]()
        result3 <- collection.delete.one(_.name $eq "name")
        found4  <- collection.findAll.cursor[Ident].collect[List]()
      } yield {
        found0 shouldBe List(item1, item2, item3, item4)
        result0.n shouldBe 1
        found1 shouldBe List(item2, item3, item4)
        result1.n shouldBe 1
        found2 shouldBe List(item2, item4)
        result2.n shouldBe 1
        found3 shouldBe List(item4)
        result3.n shouldBe 1
        found4 shouldBe empty
      }
    }
    "deleteMany" in withCollection[Data].apply { collection =>
      val item1 = record()
      val item2 = record()
      val item3 = record()
      val item4 = record()
      for {
        _       <- collection.insert[Ident].many(item1, item2, item3, item4)
        found0  <- collection.findAll.cursor[Ident].collect[List]()
        result0 <- collection.delete.many(_.name $eq "name")
        found1  <- collection.findAll.cursor[Ident].collect[List]()
      } yield {
        found0 shouldBe List(item1, item2, item3, item4)
        result0.n shouldBe 4
        found1 shouldBe empty
      }
    }
    "bulk delete" in withCollection[Data].apply { collection =>
      val item1 = record()
      val item2 = record()
      val item3 = record()
      val item4 = record()
      for {
        _       <- collection.insert[Ident].many(item1, item2, item3, item4)
        found0  <- collection.findAll.cursor[Ident].collect[List]()
        result0 <- collection.delete.bulk(
                     _.op(_._id $eq item1._id),
                     _.op(_._id $eq item3._id)
                   )
        found1  <- collection.findAll.cursor[Ident].collect[List]()
        result1 <- collection.delete.bulk(
                     _.op(_._id $eq item2._id),
                     _.op(_._id $eq item4._id)
                   )
        found2  <- collection.findAll.cursor[Ident].collect[List]()
      } yield {
        found0 shouldBe List(item1, item2, item3, item4)
        result0.n shouldBe 2
        found1 shouldBe List(item2, item4)
        result1.n shouldBe 2
        found2 shouldBe empty
      }
    }
  }

}
