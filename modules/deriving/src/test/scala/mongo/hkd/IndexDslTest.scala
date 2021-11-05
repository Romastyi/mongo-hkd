package mongo.hkd

import mongo.hkd.dsl._
import org.scalatest.Inside.inside
import reactivemongo.api.bson.document
import reactivemongo.api.indexes.IndexType

class IndexDslTest extends CommonMongoSpec {

  "ensureIndices(...)" in withCollection[Data].apply { collection =>
    for {
      _       <- collection.ensureIndices(
                   _.oid.ascending.index(name =
                     Some("idx0")
                   ), // Will not create any additional index, only duplicates the default index by `_id` field.
                   _.name.hashed.index(name = Some("idx1")),
                   fs => List(fs.name.text.weight(10), fs.description.text).index(name = Some("idx2")),
                   fs => (fs.nestedData ~ (_.id)).hashed.index(),
                   _.isActive.ascending.index(name = Some("idx4"), unique = true, sparse = true)
                 )
      indices <- collection.delegate(_.indexesManager.list())
    } yield {
      indices should have length 5
      indices.flatMap(_.name) should contain theSameElementsAs List(
        "idx1",
        "idx2",
        "nested_data.id_hashed",
        "idx4",
        "_id_"
      )
      inside(indices.find(_.name.contains("_id_")).get) { idx =>
        idx.key shouldBe List("_id" -> IndexType.Ascending)
        idx.unique shouldBe false
        idx.background shouldBe false
        idx.sparse shouldBe false
        idx.expireAfterSeconds shouldBe None
        idx.storageEngine shouldBe None
        idx.weights shouldBe None
        idx.defaultLanguage shouldBe None
        idx.languageOverride shouldBe None
        idx.textIndexVersion shouldBe None
        idx._2dsphereIndexVersion shouldBe None
        idx.bits shouldBe None
        idx.min shouldBe None
        idx.max shouldBe None
        idx.bucketSize shouldBe None
        idx.collation shouldBe None
        idx.wildcardProjection shouldBe None
        idx.version shouldBe Some(2)
        idx.partialFilter shouldBe None
        idx.options shouldBe document
      }
      inside(indices.find(_.name.contains("idx1")).get) { idx =>
        idx.key shouldBe List("name" -> IndexType.Hashed)
        idx.unique shouldBe false
        idx.background shouldBe false
        idx.sparse shouldBe false
        idx.expireAfterSeconds shouldBe None
        idx.storageEngine shouldBe None
        idx.weights shouldBe None
        idx.defaultLanguage shouldBe None
        idx.languageOverride shouldBe None
        idx.textIndexVersion shouldBe None
        idx._2dsphereIndexVersion shouldBe None
        idx.bits shouldBe None
        idx.min shouldBe None
        idx.max shouldBe None
        idx.bucketSize shouldBe None
        idx.collation shouldBe None
        idx.wildcardProjection shouldBe None
        idx.version shouldBe Some(2)
        idx.partialFilter shouldBe None
        idx.options shouldBe document
      }
      inside(indices.find(_.name.contains("idx2")).get) { idx =>
        idx.key shouldBe List("description" -> IndexType.Text, "name" -> IndexType.Ascending)
        idx.unique shouldBe false
        idx.background shouldBe false
        idx.sparse shouldBe false
        idx.expireAfterSeconds shouldBe None
        idx.storageEngine shouldBe None
        idx.weights shouldBe Some(document("name" -> 10, "description" -> 1))
        idx.defaultLanguage shouldBe Some("english")
        idx.languageOverride shouldBe Some("language")
        idx.textIndexVersion shouldBe Some(3)
        idx._2dsphereIndexVersion shouldBe None
        idx.bits shouldBe None
        idx.min shouldBe None
        idx.max shouldBe None
        idx.bucketSize shouldBe None
        idx.collation shouldBe None
        idx.wildcardProjection shouldBe None
        idx.version shouldBe Some(2)
        idx.partialFilter shouldBe None
        idx.options shouldBe document("default_language" -> "english", "language_override" -> "language")
      }
      inside(indices.find(_.name.contains("nested_data.id_hashed")).get) { idx =>
        idx.key shouldBe List("nested_data.id" -> IndexType.Hashed)
        idx.unique shouldBe false
        idx.background shouldBe false
        idx.sparse shouldBe false
        idx.expireAfterSeconds shouldBe None
        idx.storageEngine shouldBe None
        idx.weights shouldBe None
        idx.defaultLanguage shouldBe None
        idx.languageOverride shouldBe None
        idx.textIndexVersion shouldBe None
        idx._2dsphereIndexVersion shouldBe None
        idx.bits shouldBe None
        idx.min shouldBe None
        idx.max shouldBe None
        idx.bucketSize shouldBe None
        idx.collation shouldBe None
        idx.wildcardProjection shouldBe None
        idx.version shouldBe Some(2)
        idx.partialFilter shouldBe None
        idx.options shouldBe document
      }
      inside(indices.find(_.name.contains("idx4")).get) { idx =>
        idx.key shouldBe List("is_active" -> IndexType.Ascending)
        idx.unique shouldBe true
        idx.background shouldBe false
        idx.sparse shouldBe true
        idx.expireAfterSeconds shouldBe None
        idx.storageEngine shouldBe None
        idx.weights shouldBe None
        idx.defaultLanguage shouldBe None
        idx.languageOverride shouldBe None
        idx.textIndexVersion shouldBe None
        idx._2dsphereIndexVersion shouldBe None
        idx.bits shouldBe None
        idx.min shouldBe None
        idx.max shouldBe None
        idx.bucketSize shouldBe None
        idx.collation shouldBe None
        idx.wildcardProjection shouldBe None
        idx.version shouldBe Some(2)
        idx.partialFilter shouldBe None
        idx.options shouldBe document
      }
    }
  }

}
