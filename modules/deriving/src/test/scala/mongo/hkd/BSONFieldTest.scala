package mongo.hkd

import mongo.hkd.dsl._
import mongo.hkd.implicits._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import reactivemongo.api.bson.BSONValue.pretty
import reactivemongo.api.bson._

import java.util.UUID

class BSONFieldTest extends AnyFreeSpec with Matchers {

  "BSONField" - {
    "deriving" in {
      BSONField.fields[Data].nestedData.nested(_.id).fieldName should be("nested_data.id")
      (BSONField.fields[Data].nestedData ~ (_.id)).fieldName should be("nested_data.id")
      BSONField.fields[Data].nestedData.nested(_.secondField).fieldName should be("nested_data.second_field")
      (BSONField.fields[Data].nestedData ~ (_.secondField)).fieldName should be("nested_data.second_field")
      (BSONField.fields[Data].otherData ~ (_.id)).fieldName should be("other_data.id")
      (BSONField.fields[Data].otherData ~ (_.secondField)).fieldName should be("other_data.second_field")
    }
    "codecs" in {
      val nested    = NestedData[Id](UUID.randomUUID(), None)
      val data      =
        Data[Id](1, "name", Some("description"), true, List("tag1", "tag2"), nested, List(nested))
      val optNested = NestedData[Option](Some(nested.id), None)
      val optData   = Data[Option](
        Some(data.id),
        Some(data.name),
        Some(data.description),
        Some(data.isActive),
        Some(data.tags),
        Some(optNested),
        Some(List(optNested))
      )

      val bson = BSON.write(data).get
      pretty(bson) should be(s"""{
                               |  'id': 1,
                               |  'name': 'name',
                               |  'description': 'description',
                               |  'is_active': true,
                               |  'tags': [
                               |    'tag1',
                               |    'tag2'
                               |  ],
                               |  'nested_data': {
                               |    'id': '${nested.id}',
                               |    'second_field': null
                               |  },
                               |  'other_data': [
                               |    {
                               |      'id': '${nested.id}',
                               |      'second_field': null
                               |    }
                               |  ]
                               |}""".stripMargin)
      BSON.read[Data[Id]](bson).get should be(data)
      BSON.read[Data[Option]](bson).get should be(optData)
      pretty(BSON.write(optData).get) should be(pretty(bson))
    }
  }

  "BSONRecord" - {
    "codecs" in {
      val oid       = BSONObjectID.generate()
      val nested    = NestedData[Id](UUID.randomUUID(), None)
      val data      =
        Data[Id](1, "name", Some("description"), true, List("tag1", "tag2"), nested, List(nested)).record(oid)
      val optNested = NestedData[Option](Some(data.data.nestedData.id), None)
      val optData   = Data[Option](
        Some(data.data.id),
        Some(data.data.name),
        Some(data.data.description),
        Some(data.data.isActive),
        Some(data.data.tags),
        Some(optNested),
        Some(List(optNested))
      ).record(Some(oid))

      val bson = BSON.write(data).get
      pretty(bson) should be(s"""{
                                |  '_id': ObjectId('${oid.stringify}'),
                                |  'id': 1,
                                |  'name': 'name',
                                |  'description': 'description',
                                |  'is_active': true,
                                |  'tags': [
                                |    'tag1',
                                |    'tag2'
                                |  ],
                                |  'nested_data': {
                                |    'id': '${nested.id}',
                                |    'second_field': null
                                |  },
                                |  'other_data': [
                                |    {
                                |      'id': '${nested.id}',
                                |      'second_field': null
                                |    }
                                |  ]
                                |}""".stripMargin)
      BSON.read[Data[Id]](bson).get should be(data.data)
      BSON.read[BSONRecord[Data, Id]](bson).get should be(data)
      BSON.read[Data[Option]](bson).get should be(optData.data)
      BSON.read[BSONRecord[Data, Option]](bson).get should be(optData)
      pretty(BSON.write(optData).get) should be(pretty(bson))
    }
  }

}
