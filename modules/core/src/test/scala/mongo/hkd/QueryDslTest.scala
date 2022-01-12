package mongo.hkd

import mongo.hkd.dsl._
import mongo.hkd.implicits._
import reactivemongo.api.bson.BSONValue.pretty
import reactivemongo.api.bson._

import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class QueryDslTest extends CommonMongoSpec {

  val uuid1   = new UUID(0, 0)
  val nested1 = NestedData[Ident](uuid1, Some(1), Some("field"))
  val data1   = Data[Ident](1, "name1", Some("str"), true, List("tag1", "tag2"), nested1, Some(List(nested1)))
  val oid2    = BSONObjectID.generate()
  val uuid2   = new UUID(0, 1)
  val nested2 = NestedData[Ident](uuid2, Some(2), None)
  val data2   = Data[Ident](2, "name2", None, false, List("tag2", "tag3"), nested2, Some(List(nested2)))

  override def afterStart(): Unit = {
    Await.result(
      Future { Thread.sleep(10000) }.flatMap { _ =>
        withCollection[Data].apply { collection =>
          for {
            _ <- collection.insert.many(data1, data2.record(oid2))
          } yield ()
        }
      },
      25 seconds
    )
  }

  "Query" - {
    val fields = BSONField.fields[Data]
    "match dsl" in {
      val query1: Query = (fields.name m "name") ::
        (fields.isActive m true) ::
        (fields.nestedData./.secondField m Some("one")) ::
        Nil
      pretty(query1.bson) should be("""{
          |  'name': 'name',
          |  'is_active': true,
          |  'nested_data.second_field': 'one'
          |}""".stripMargin)
      val query2: Query = (fields.name m "name") ::
        (fields.isActive m true) ::
        (fields.nestedData m NestedData[Ident](id = uuid1, firstField = Some(1), secondField = Some("one"))) ::
        Nil
      pretty(query2.bson) should be("""{
          |  'name': 'name',
          |  'is_active': true,
          |  'nested_data': {
          |    'id': '00000000-0000-0000-0000-000000000000',
          |    'first_field': NumberLong(1),
          |    'second_field': 'one'
          |  }
          |}""".stripMargin)
    }
    "condition dsl" in {
      val query =
        (((fields.name $eq "name") $or (fields.name $regex "/[0-9]+/"))
          $and (fields.isActive $not (_ $eq true))
          $and (fields.description $regex "/str/")) $or
          (fields.nestedData./.secondField $in (Some("one"), Some("two"), None))
      pretty(query.bson) should be(f"""{
          |  '$$or': [
          |    {
          |      '$$and': [
          |        {
          |          '$$and': [
          |            {
          |              '$$or': [
          |                {
          |                  'name': {
          |                    '$$eq': 'name'
          |                  }
          |                },
          |                {
          |                  'name': {
          |                    '$$regex': //[0-9]+//
          |                  }
          |                }
          |              ]
          |            },
          |            {
          |              'is_active': {
          |                '$$not': {
          |                  '$$eq': true
          |                }
          |              }
          |            }
          |          ]
          |        },
          |        {
          |          'description': {
          |            '$$regex': //str//
          |          }
          |        }
          |      ]
          |    },
          |    {
          |      'nested_data.second_field': {
          |        '$$in': [
          |          'one',
          |          'two',
          |          null
          |        ]
          |      }
          |    }
          |  ]
          |}""".stripMargin)
    }
    "findQuery" in withCollection[Data].apply { collection =>
      val oid1 = BSONObjectID.generate()
      for {
        found0  <- collection.findAll
                     .sort(_.id.desc)
                     .cursor[Ident]
                     .collect[List]()
        found1  <- collection
                     .findQuery(_.id $eq 1)
                     .requireOne[Ident]
        found2  <- collection
                     .findQuery(_.id $eq 2)
                     .one[Ident]
        found3  <- collection
                     .findQuery(_.id $eq 3)
                     .one[Ident]
        found4  <- collection
                     .findQuery(_.name $regex """(name)\d?""")
                     .cursor[Ident]
                     .collect[List]()
        found5  <- collection
                     .findQuery(fs => (fs.name $regex """(name)\d?""") $and (fs.description $eq None))
                     .cursor[Ident]
                     .collect[List]()
        found6  <- collection
                     .findQuery(_.nestedData m nested1)
                     .requireOne[Ident]
        found7  <- collection
                     .findQuery(_.nestedData./.id $in (uuid1, uuid2))
                     .cursor[Ident]
                     .collect[List]()
        found8  <- collection
                     .findQuery(_.nestedData./.id $not (_ $eq uuid1))
                     .cursor[Ident]
                     .collect[List]()
        found9  <- collection
                     .findQuery(fs => (fs.id $eq 1) $or (fs.nestedData m nested2))
                     .cursor[Ident]
                     .collect[List]()
        found10 <- collection.findQuery(_._id $in (oid1, oid2)).cursor[Ident].collect[List]()
        found11 <- collection
                     .findQuery(_.otherData $all (_ $elemMatch (_.id $eq uuid2)))
                     .cursor[Ident]
                     .collect[List]()
        found12 <- collection.findQuery(_.tags $all ("tag1", "tag2")).cursor[Ident].collect[List]()
        found13 <-
          collection.findQuery(fs => (fs.tags $size 2) $or (fs.otherData $size 2)).cursor[Ident].collect[List]()
      } yield {
        found0.map(_.data) should be(data2 :: data1 :: Nil)
        found1._id should not be oid1
        found1.data should be(data1)
        found2.map(_._id) should be(Some(oid2))
        found2.map(_.data) should be(Some(data2))
        found3.map(_.data) should be(None)
        found4.map(_.data) should be(data1 :: data2 :: Nil)
        found5.map(_.data) should be(data2 :: Nil)
        found6.data should be(data1)
        found7.map(_.data) should be(data1 :: data2 :: Nil)
        found8.map(_.data) should be(data2 :: Nil)
        found8.map(_.data) should be(data2 :: Nil)
        found9.map(_.data) should be(data1 :: data2 :: Nil)
        found10.map(_.data) should be(data2 :: Nil)
        found11.map(_.data) should be(data2 :: Nil)
        found12.map(_.data) should be(data1 :: Nil)
        found13.map(_.data) should be(data1 :: data2 :: Nil)
      }
    }
    "projection" in withCollection[Data].apply { collection =>
      for {
        found0 <- collection.findAll
                    .projection(_._id -> incl)
                    .sort(_.id.asc)
                    .cursor[Option]
                    .collect[List]()
        found1 <- collection.findAll
                    .projection(_._id -> excl, _.id -> incl)
                    .cursor[Option]
                    .collect[List]()
        found2 <- collection.findAll
                    .projection(_._id -> excl, _.id -> incl, _.nestedData -> incl)
                    .cursor[Option]
                    .collect[List]()
        found3 <- collection.findAll
                    .projection(_._id -> incl, _.id -> incl, _.nestedData./.id -> incl)
                    .cursor[Option]
                    .collect[List]()
        found4 <- collection
                    .findQuery(_.tags $regex """(tag)\d""")
                    .projection(_.tags.$ -> incl)
                    .cursor[Option]
                    .collect[List]()
        found5 <- collection
                    .findQuery(_.tags $regex """(tag)\d""")
                    .projection(_.tags slice -1, _.otherData $elemMatch (_.id $eq uuid2))
                    .cursor[Option]
                    .collect[List]()
        found6 <- collection
                    .findQuery(_.tags $regex """(tag)\d""")
                    .projection(_.tags slice (1, 1))
                    .cursor[Option]
                    .collect[List]()
      } yield {
        val oid1      = found0.head._id.get
        val emptyData = Data[Option](None, None, None, None, None, None, None)
        found0 should have length 2
        found0 should be(
          List(
            BSONRecord(Some(oid1), emptyData),
            BSONRecord(Some(oid2), emptyData)
          )
        )
        found1 should be(
          List(
            BSONRecord[Data, Option](None, emptyData.copy(id = Some(1))),
            BSONRecord[Data, Option](None, emptyData.copy(id = Some(2)))
          )
        )
        found2 should be(
          List(
            BSONRecord[Data, Option](
              None,
              emptyData
                .copy(
                  id = Some(1),
                  nestedData = Some(NestedData[Option](Some(uuid1), Some(Some(1)), Some(Some("field"))))
                )
            ),
            BSONRecord[Data, Option](
              None,
              emptyData.copy(id = Some(2), nestedData = Some(NestedData[Option](Some(uuid2), Some(Some(2)), None)))
            )
          )
        )
        found3 should be(
          List(
            BSONRecord(
              Some(oid1),
              emptyData.copy(id = Some(1), nestedData = Some(NestedData[Option](Some(uuid1), None, None)))
            ),
            BSONRecord(
              Some(oid2),
              emptyData.copy(id = Some(2), nestedData = Some(NestedData[Option](Some(uuid2), None, None)))
            )
          )
        )
        found4 should be(
          List(
            BSONRecord(Some(oid1), emptyData.copy(tags = Some(List("tag1")))),
            BSONRecord(Some(oid2), emptyData.copy(tags = Some(List("tag2"))))
          )
        )
        found5 should be(
          List(
            BSONRecord(
              Some(oid1),
              Data[Option](
                Some(data1.id),
                Some(data1.name),
                Some(data1.description),
                Some(data1.isActive),
                Some(List("tag2")),
                Some(NestedData[Option](Some(nested1.id), Some(nested1.firstField), Some(nested1.secondField))),
                None
              )
            ),
            BSONRecord(
              Some(oid2),
              Data[Option](
                Some(data2.id),
                Some(data2.name),
                None,
                Some(data2.isActive),
                Some(List("tag3")),
                Some(NestedData[Option](Some(nested2.id), Some(nested2.firstField), None)),
                Some(Some(List(NestedData[Option](Some(nested2.id), Some(nested2.firstField), None))))
              )
            )
          )
        )
        found6 should be(
          List(
            BSONRecord(
              Some(oid1),
              Data[Option](
                Some(data1.id),
                Some(data1.name),
                Some(data1.description),
                Some(data1.isActive),
                Some(List("tag2")),
                Some(NestedData[Option](Some(nested1.id), Some(nested1.firstField), Some(nested1.secondField))),
                Some(
                  Some(List(NestedData[Option](Some(nested1.id), Some(nested1.firstField), Some(nested1.secondField))))
                )
              )
            ),
            BSONRecord(
              Some(oid2),
              Data[Option](
                Some(data2.id),
                Some(data2.name),
                None,
                Some(data2.isActive),
                Some(List("tag3")),
                Some(NestedData[Option](Some(nested2.id), Some(nested2.firstField), None)),
                Some(Some(List(NestedData[Option](Some(nested2.id), Some(nested2.firstField), None))))
              )
            )
          )
        )
      }
    }
  }
}
