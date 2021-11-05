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

  val uuid1 = new UUID(0, 0)
  val data1 = Data[Id](1, "name1", Some("str"), true, NestedData[Id](uuid1, Some("field")))
  val uuid2 = new UUID(0, 1)
  val data2 = Data[Id](2, "name2", None, false, NestedData[Id](uuid2, None))

  override def afterStart(): Unit = {
    Await.result(
      Future { Thread.sleep(10000) }.flatMap { _ =>
        withCollection[Data].apply { collection =>
          for {
            _ <- collection.delegate(_.insert(false).many(Seq(data1, data2)))
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
        ((fields.nestedData ~ (_.secondField)) m Some("one")) ::
        Nil
      pretty(query1.bson) should be("""{
          |  'name': 'name',
          |  'is_active': true,
          |  'nested_data.second_field': 'one'
          |}""".stripMargin)
      val query2: Query = (fields.name m "name") ::
        (fields.isActive m true) ::
        (fields.nestedData m NestedData[Id](id = uuid1, secondField = Some("one"))) ::
        Nil
      pretty(query2.bson) should be("""{
          |  'name': 'name',
          |  'is_active': true,
          |  'nested_data': {
          |    'id': '00000000-0000-0000-0000-000000000000',
          |    'second_field': 'one'
          |  }
          |}""".stripMargin)
    }
    "condition dsl" in {
      val query =
        (((fields.name $eq "name") $or (fields.name $regex "/[0-9]+/"))
          $and (fields.isActive $not (_ $eq true))
          $and (fields.description $regex "/str/")) $or
          ((fields.nestedData ~ (_.secondField)) $in (Some("one"), Some("two"), None))
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
          |                    '$$regex': BSONRegex(/[0-9]+/, )
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
          |            '$$regex': BSONRegex(/str/, )
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
      for {
        found0 <- collection.findAll
                    .options(_.sort(document("id" -> -1)))
                    .cursor[Id]
                    .collect[List]()
        found1 <- collection
                    .findQuery(_.id $eq 1)
                    .one[Id]
        found2 <- collection
                    .findQuery(_.id $eq 2)
                    .one[Id]
        found3 <- collection
                    .findQuery(_.id $eq 3)
                    .one[Id]
        found4 <- collection
                    .findQuery(_.name $regex """(name)\d?""")
                    .cursor[Id]
                    .collect[List]()
        found5 <- collection
                    .findQuery(fs => (fs.name $regex """(name)\d?""") $and (fs.description $eq None))
                    .cursor[Id]
                    .collect[List]()
        found6 <- collection
                    .findQuery(_.nestedData m data1.nestedData)
                    .requireOne[Id]
        found7 <- collection
                    .findQuery(_.nestedData ~ (_.id) $in (uuid1, uuid2))
                    .cursor[Id]
                    .collect[List]()
        found8 <- collection
                    .findQuery(_.nestedData ~ (_.id) $not (_ $eq uuid1))
                    .cursor[Id]
                    .collect[List]()
        found9 <- collection
                    .findQuery(fs => (fs.id $eq 1) $or (fs.nestedData m data2.nestedData))
                    .cursor[Id]
                    .collect[List]()
      } yield {
        found0 should be(data2 :: data1 :: Nil)
        found1 should be(Some(data1))
        found2 should be(Some(data2))
        found3 should be(None)
        found4 should be(data1 :: data2 :: Nil)
        found5 should be(data2 :: Nil)
        found6 should be(data1)
        found7 should be(data1 :: data2 :: Nil)
        found8 should be(data2 :: Nil)
        found8 should be(data2 :: Nil)
        found9 should be(data1 :: data2 :: Nil)
      }
    }
  }
}
