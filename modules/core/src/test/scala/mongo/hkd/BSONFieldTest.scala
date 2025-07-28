package mongo.hkd

import mongo.hkd.dsl.*
import mongo.hkd.implicits.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import reactivemongo.api.bson.BSONValue.pretty
import reactivemongo.api.bson.*

import java.util.UUID

class BSONFieldTest extends AnyFreeSpec with Matchers {

  "BSONField" - {

    case class Fields(private val f: BSONField.Fields[Data], private val n: BSONField.Fields[NestedData]) {
      implicit val fields: BSONField.Fields[Data]       = f
      implicit val nested: BSONField.Fields[NestedData] = n
    }

    @inline def withNaming[T](
        naming: String => String
    )(body: Fields => T): T =
      body(Fields(deriving.fields[Data](naming), deriving.fields[NestedData](naming)))

    "deriving" - {
      "identity" in withNaming(identity) { fs =>
        import fs.*
        fields.id.fieldName should be("id")
        fields.description.fieldName should be("description")
        fields.isActive.fieldName should be("isActive")
        fields.nestedData.fieldName should be("nestedData")
        fields.otherData.fieldName should be("otherData")
        fields.nestedData./.id.fieldName should be("nestedData.id")
        fields.nestedData./.id.fieldName should be("nestedData.id")
        fields.nestedData./.secondField.fieldName should be("nestedData.secondField")
        fields.nestedData./.secondField.fieldName should be("nestedData.secondField")
        fields.otherData./.id.fieldName should be("otherData.id")
        fields.otherData./.secondField.fieldName should be("otherData.secondField")
      }
      "snakeCase" in withNaming(renaming.snakeCase) { fs =>
        import fs.*
        fields.id.fieldName should be("id")
        fields.description.fieldName should be("description")
        fields.isActive.fieldName should be("is_active")
        fields.nestedData.fieldName should be("nested_data")
        fields.otherData.fieldName should be("other_data")
        fields.nestedData./.id.fieldName should be("nested_data.id")
        fields.nestedData./.id.fieldName should be("nested_data.id")
        fields.nestedData./.secondField.fieldName should be("nested_data.second_field")
        fields.nestedData./.secondField.fieldName should be("nested_data.second_field")
        fields.otherData./.id.fieldName should be("other_data.id")
        fields.otherData./.secondField.fieldName should be("other_data.second_field")
      }
      "kebabCase" in withNaming(renaming.kebabCase) { fs =>
        import fs.*
        fields.id.fieldName should be("id")
        fields.description.fieldName should be("description")
        fields.isActive.fieldName should be("is-active")
        fields.nestedData.fieldName should be("nested-data")
        fields.otherData.fieldName should be("other-data")
        fields.nestedData./.id.fieldName should be("nested-data.id")
        fields.nestedData./.id.fieldName should be("nested-data.id")
        fields.nestedData./.secondField.fieldName should be("nested-data.second-field")
        fields.nestedData./.secondField.fieldName should be("nested-data.second-field")
        fields.otherData./.id.fieldName should be("other-data.id")
        fields.otherData./.secondField.fieldName should be("other-data.second-field")
      }
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
