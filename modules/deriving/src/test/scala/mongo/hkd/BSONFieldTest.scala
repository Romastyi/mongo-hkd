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
    }
    "codecs" in {
      val data    = Data[Id](1, "name", Some("description"), true, NestedData[Id](UUID.randomUUID(), None))
      val optData = Data[Option](
        Some(data.id),
        Some(data.name),
        Some(data.description),
        Some(data.isActive),
        Some(NestedData[Option](Some(data.nestedData.id), None))
      )

      val bson = BSON.write(data).get
      pretty(bson) should be(s"""{
                               |  'id': 1,
                               |  'name': 'name',
                               |  'description': 'description',
                               |  'is_active': true,
                               |  'nested_data': {
                               |    'id': '${data.nestedData.id}',
                               |    'second_field': null
                               |  }
                               |}""".stripMargin)
      BSON.read[Data[Id]](bson).get should be(data)
      BSON.read[Data[Option]](bson).get should be(optData)
      pretty(BSON.write(optData).get) should be(pretty(bson))
    }
  }

  "BSONRecord" - {
    "codecs" in {
      val oid     = BSONObjectID.generate()
      val data    = Data[Id](1, "name", Some("description"), true, NestedData[Id](UUID.randomUUID(), None)).record(oid)
      val optData = Data[Option](
        Some(data.data.id),
        Some(data.data.name),
        Some(data.data.description),
        Some(data.data.isActive),
        Some(NestedData[Option](Some(data.data.nestedData.id), None))
      ).record(Some(oid))

      val bson = BSON.write(data).get
      pretty(bson) should be(s"""{
                                |  '_id': ObjectId('${oid.stringify}'),
                                |  'id': 1,
                                |  'name': 'name',
                                |  'description': 'description',
                                |  'is_active': true,
                                |  'nested_data': {
                                |    'id': '${data.data.nestedData.id}',
                                |    'second_field': null
                                |  }
                                |}""".stripMargin)
      BSON.read[Data[Id]](bson).get should be(data.data)
      BSON.read[BSONRecord[Data, Id]](bson).get should be(data)
      BSON.read[Data[Option]](bson).get should be(optData.data)
      BSON.read[BSONRecord[Data, Option]](bson).get should be(optData)
      pretty(BSON.write(optData).get) should be(pretty(bson))
    }
  }

}
