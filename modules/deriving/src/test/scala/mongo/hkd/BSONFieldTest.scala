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
      val nested    = NestedData[Ident](UUID.randomUUID(), Some(1), None)
      val data      =
        Data[Ident](1, "name", Some("description"), true, List("tag1", "tag2"), nested, Some(List(nested)))
      val optNested = NestedData[Option](Some(nested.id), Some(nested.firstField), None)
      val optData   = Data[Option](
        Some(data.id),
        Some(data.name),
        Some(data.description),
        Some(data.isActive),
        Some(data.tags),
        Some(optNested),
        Some(Some(List(optNested)))
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
                               |    'first_field': NumberLong(1),
                               |    'second_field': null
                               |  },
                               |  'other_data': [
                               |    {
                               |      'id': '${nested.id}',
                               |      'first_field': NumberLong(1),
                               |      'second_field': null
                               |    }
                               |  ]
                               |}""".stripMargin)
      BSON.read[Data[Ident]](bson).get should be(data)
      BSON.read[Data[Option]](bson).get should be(optData)
      pretty(BSON.write(optData).get) should be(pretty(bson))
    }
  }

  "BSONRecord" - {
    "codecs" in {
      val oid       = BSONObjectID.generate()
      val nested    = NestedData[Ident](UUID.randomUUID(), Some(1), None)
      val data      =
        Data[Ident](1, "name", Some("description"), true, List("tag1", "tag2"), nested, None).record(oid)
      val optNested = NestedData[Option](Some(nested.id), Some(nested.firstField), None)
      val optData   = Data[Option](
        Some(data.data.id),
        Some(data.data.name),
        Some(data.data.description),
        Some(data.data.isActive),
        Some(data.data.tags),
        Some(optNested),
        None
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
                                |    'first_field': NumberLong(1),
                                |    'second_field': null
                                |  },
                                |  'other_data': null
                                |}""".stripMargin)
      BSON.read[Data[Ident]](bson).get should be(data.data)
      BSON.read[BSONRecord[Data, Ident]](bson).get should be(data)
      BSON.read[Data[Option]](bson).get should be(optData.data)
      BSON.read[BSONRecord[Data, Option]](bson).get should be(optData)
      pretty(BSON.write(optData).get) should be(pretty(bson))
    }
  }

}
