package mongo.hkd

import reactivemongo.api.Collation
import reactivemongo.api.bson.collection.BSONSerializationPack
import reactivemongo.api.bson.{BSONDocument, BSONElement, BSONObjectID, document}
import reactivemongo.api.indexes.{Index, IndexType}

import scala.annotation.nowarn
import scala.concurrent.{ExecutionContext, Future}

sealed trait ScalaTypeMongoIndexTypeMapping[A, T <: IndexType]

@nowarn("msg=is never used")
object ScalaTypeMongoIndexTypeMapping {

  type Ged2D[A]          = ScalaTypeMongoIndexTypeMapping[A, IndexType.Geo2D.type]
  type Geo2DSpherical[A] = ScalaTypeMongoIndexTypeMapping[A, IndexType.Geo2DSpherical.type]
  type GeoHaystack[A]    = ScalaTypeMongoIndexTypeMapping[A, IndexType.GeoHaystack.type]
  type Text[A]           = ScalaTypeMongoIndexTypeMapping[A, IndexType.Text.type]
  type Hashed[A]         = ScalaTypeMongoIndexTypeMapping[A, IndexType.Hashed.type]
  type Ascending[A]      = ScalaTypeMongoIndexTypeMapping[A, IndexType.Ascending.type]
  type Descending[A]     = ScalaTypeMongoIndexTypeMapping[A, IndexType.Descending.type]
  type Ordered[A]        = Ascending[A] with Descending[A]

  def geo2D[A]: Ged2D[A]                   = null
  def geo2DSpherical[A]: Geo2DSpherical[A] = null
  def geoHaystack[A]: GeoHaystack[A]       = null
  def text[A]: Text[A]                     = null
  def hashed[A]: Hashed[A]                 = null
  def ordered[A]: Ordered[A]               = null

  // Default instances
  implicit def optionMapping[T <: IndexType, A](implicit
      mapping: ScalaTypeMongoIndexTypeMapping[A, T]
  ): ScalaTypeMongoIndexTypeMapping[Option[A], T] = null
  implicit def arrayMapping[T <: IndexType, A](implicit
      mapping: ScalaTypeMongoIndexTypeMapping[A, T]
  ): ScalaTypeMongoIndexTypeMapping[List[A], T]   = null

  implicit def stringText: Text[String]                   = text
  implicit def defaultOrdered[A: Ordering]: Ordered[A]    = ordered
  implicit val bsonObjectIdOrdered: Ordered[BSONObjectID] = ordered
  implicit def anyToIndexType[A]: Hashed[A]               = hashed

}

sealed trait BSONFieldIndexKey[+T <: IndexType] {
  def fieldName: String
  def indexType: IndexType
  def weight: Option[Int]
}

object BSONFieldIndexKey {

  private case class Impl[T <: IndexType](
      override val fieldName: String,
      override val indexType: IndexType,
      override val weight: Option[Int]
  ) extends BSONFieldIndexKey[T]

  def apply[A, T <: IndexType](field: BSONField[A], indexType: T): BSONFieldIndexKey[T] =
    Impl[T](field.fieldName, indexType, None)

  implicit class TextKeyOps(private val key: BSONFieldIndexKey[IndexType.Text.type]) extends AnyVal {
    def weight(w: Int): BSONFieldIndexKey[IndexType.Text.type] = Impl(key.fieldName, key.indexType, Some(w))
  }

}

sealed trait IndexBuilder {
  def build: Index.Default
}

object IndexBuilder {
  def apply(
      keys: Seq[BSONFieldIndexKey[IndexType]],
      name: Option[String] = None,
      unique: Boolean = false,
      background: Boolean = false,
      sparse: Boolean = false,
      expireAfterSeconds: Option[Int] = None,
      storageEngine: Option[BSONDocument] = None,
      defaultLanguage: Option[String] = None,
      languageOverride: Option[String] = None,
      textIndexVersion: Option[Int] = None,
      sphereIndexVersion: Option[Int] = None,
      bits: Option[Int] = None,
      min: Option[Double] = None,
      max: Option[Double] = None,
      bucketSize: Option[Double] = None,
      collation: Option[Collation] = None,
      wildcardProjection: Option[BSONDocument] = None,
      version: Option[Int] = None,
      partialFilter: Option[BSONDocument] = None,
      options: BSONDocument = document
  ): IndexBuilder = new IndexBuilder {
    override def build: Index.Default =
      Index(BSONSerializationPack)(
        key = keys.map { key => key.fieldName -> key.indexType },
        name = name,
        unique = unique,
        background = background,
        sparse = sparse,
        expireAfterSeconds = expireAfterSeconds,
        storageEngine = storageEngine,
        weights = Some(keys.flatMap { key => key.weight.map(w => BSONElement(key.fieldName, w)) })
          .filter(_.nonEmpty)
          .map(weights => document(weights: _*)),
        defaultLanguage = defaultLanguage,
        languageOverride = languageOverride,
        textIndexVersion = textIndexVersion,
        sphereIndexVersion = sphereIndexVersion,
        bits = bits,
        min = min,
        max = max,
        bucketSize = bucketSize,
        collation = collation,
        wildcardProjection = wildcardProjection,
        version = version,
        partialFilter = partialFilter,
        options = options
      )
  }
}

trait IndexDsl extends IndexDslLowPriorityImplicits {

  @nowarn("msg=is never used")
  implicit class BSONFieldIndexOps[A](private val field: BSONField[A]) {
    def ascending(implicit
        mapping: ScalaTypeMongoIndexTypeMapping.Ascending[A]
    ): BSONFieldIndexKey[IndexType.Ascending.type]                                                                   =
      BSONFieldIndexKey(field, IndexType.Ascending)
    def descending(implicit
        mapping: ScalaTypeMongoIndexTypeMapping.Descending[A]
    ): BSONFieldIndexKey[IndexType.Descending.type]                                                                  =
      BSONFieldIndexKey(field, IndexType.Descending)
    def hashed(implicit mapping: ScalaTypeMongoIndexTypeMapping.Hashed[A]): BSONFieldIndexKey[IndexType.Hashed.type] =
      BSONFieldIndexKey(field, IndexType.Hashed)
    def text(implicit mapping: ScalaTypeMongoIndexTypeMapping.Text[A]): BSONFieldIndexKey[IndexType.Text.type]       =
      BSONFieldIndexKey(field, IndexType.Text)
    def geo2D(implicit mapping: ScalaTypeMongoIndexTypeMapping.Ged2D[A]): BSONFieldIndexKey[IndexType.Geo2D.type]    =
      BSONFieldIndexKey(field, IndexType.Geo2D)
    def geo2DSpherical(implicit
        mapping: ScalaTypeMongoIndexTypeMapping.Geo2DSpherical[A]
    ): BSONFieldIndexKey[IndexType.Geo2DSpherical.type]                                                              =
      BSONFieldIndexKey(field, IndexType.Geo2DSpherical)
    def geoHaystack(implicit
        mapping: ScalaTypeMongoIndexTypeMapping.GeoHaystack[A]
    ): BSONFieldIndexKey[IndexType.GeoHaystack.type]                                                                 =
      BSONFieldIndexKey(field, IndexType.GeoHaystack)
  }

  implicit class BSONFieldIndexKeyOps[T <: IndexType](private val key: BSONFieldIndexKey[T]) {
    def index(
        name: Option[String] = None,
        unique: Boolean = false,
        background: Boolean = false,
        sparse: Boolean = false,
        expireAfterSeconds: Option[Int] = None,
        storageEngine: Option[BSONDocument] = None,
        defaultLanguage: Option[String] = None,
        languageOverride: Option[String] = None,
        textIndexVersion: Option[Int] = None,
        sphereIndexVersion: Option[Int] = None,
        bits: Option[Int] = None,
        min: Option[Double] = None,
        max: Option[Double] = None,
        bucketSize: Option[Double] = None,
        collation: Option[Collation] = None,
        wildcardProjection: Option[BSONDocument] = None,
        version: Option[Int] = None,
        partialFilter: Option[BSONDocument] = None,
        options: BSONDocument = document
    ): IndexBuilder = IndexBuilder(
      keys = List(key),
      name = name,
      unique = unique,
      background = background,
      sparse = sparse,
      expireAfterSeconds = expireAfterSeconds,
      storageEngine = storageEngine,
      defaultLanguage = defaultLanguage,
      languageOverride = languageOverride,
      textIndexVersion = textIndexVersion,
      sphereIndexVersion = sphereIndexVersion,
      bits = bits,
      min = min,
      max = max,
      bucketSize = bucketSize,
      collation = collation,
      wildcardProjection = wildcardProjection,
      version = version,
      partialFilter = partialFilter,
      options = options
    )
  }

  implicit class BSONFieldIndexKeySeqOps(private val keys: Seq[BSONFieldIndexKey[IndexType]]) {
    def index(
        name: Option[String] = None,
        unique: Boolean = false,
        background: Boolean = false,
        sparse: Boolean = false,
        expireAfterSeconds: Option[Int] = None,
        storageEngine: Option[BSONDocument] = None,
        defaultLanguage: Option[String] = None,
        languageOverride: Option[String] = None,
        textIndexVersion: Option[Int] = None,
        sphereIndexVersion: Option[Int] = None,
        bits: Option[Int] = None,
        min: Option[Double] = None,
        max: Option[Double] = None,
        bucketSize: Option[Double] = None,
        collation: Option[Collation] = None,
        wildcardProjection: Option[BSONDocument] = None,
        version: Option[Int] = None,
        partialFilter: Option[BSONDocument] = None,
        options: BSONDocument = document
    ): IndexBuilder = IndexBuilder(
      keys = keys,
      name = name,
      unique = unique,
      background = background,
      sparse = sparse,
      expireAfterSeconds = expireAfterSeconds,
      storageEngine = storageEngine,
      defaultLanguage = defaultLanguage,
      languageOverride = languageOverride,
      textIndexVersion = textIndexVersion,
      sphereIndexVersion = sphereIndexVersion,
      bits = bits,
      min = min,
      max = max,
      bucketSize = bucketSize,
      collation = collation,
      wildcardProjection = wildcardProjection,
      version = version,
      partialFilter = partialFilter,
      options = options
    )
  }

  implicit class IndexOps[Data[f[_]]](collection: HKDBSONCollection[Data]) {
    def ensureIndices(
        index: Record.RecordFields[Data] => IndexBuilder,
        others: (Record.RecordFields[Data] => IndexBuilder)*
    )(implicit ec: ExecutionContext): Future[Unit] = {
      val manager = collection.delegate(_.indexesManager)
      Future
        .traverse(index +: others) { builder =>
          manager.ensure(builder(collection.fields).build)
        }
        .map(_ => ())
    }
  }

}

trait IndexDslLowPriorityImplicits {}
