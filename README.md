# mongo-hkd

MongoHKD is a fully-typed extension for the [ReactiveMongo](http://reactivemongo.org/) driver based on Higher-Kinded Data. 
The library provides BSON codecs deriving and query building DSL very similar to the original MongoDB query DSL.

The base concept is so-called Higher-Kinded Data. You can find description of this concept in this [blog post](https://reasonablypolymorphic.com/blog/higher-kinded-data/) (Haskell) and in this [repo](https://github.com/Michaelt293/higher-kinded-data). 

So at very beginning you need to define data classes describing your data model in Higher-Kinded Data terms, e.g.:

```scala
final case class Data[F[_]](
    id: F[Int],
    name: F[String],
    description: F[Option[String]],
    isActive: F[Boolean],
    tags: F[List[String]],
    nestedData: F[NestedData[F]],
    otherData: F[Option[List[NestedData[F]]]]
)

final case class NestedData[F[_]](
    id: F[java.util.UUID],
    firstField: F[Option[Long]],
    secondField: F[Option[String]]
)
```

### BSONField

First of all, you must define instances of your HKD data classes with `BSONField[A]` as `F[_]`, e.g. `Data[BSONField]`. 
These instances will define namings for all BSON fields of your data classes and will be used further for BSON codecs and query DSL.
These instances can be defined manually or derived automatically, e.g.:

```scala
import mongo.hkd._

/* ... */

object Data extends deriving.Fields[Data] {
  /* ... */
}

/* ... */

object NestedData {
  implicit val fields: BSONField.Fields[NestedData] = deriving.fields[NestedData](renaming.snakeCase)
  /* ... */
}
```

By default, all fields namings will be defined as the original fields names, 
but this rule can be overridden passing renaming function to `deriving.Fields`, e.g.:

```scala
object Data extends deriving.Fields[Data](renaming.snakeCase)
```

### BSON codecs

Instances for `BSONReader` and `BSONWriter` can be also derived based on `BSONField` instance. 
So for these codecs fields naming defined in `BSONField` instance will be used. 
In addition, it is necessary to parameterize `F[_]` respect to which the BSON codecs instances will be defined. 
For example:

```scala
type Ident[A] = A

object Data
    extends deriving.Fields[Data](renaming.snakeCase) with deriving.Writer[Data, Ident]
    with deriving.Reader[Data, Ident] {
  implicit val optReader: BSONDocumentReader[Data[Option]] = deriving.reader[Data, Option]
  implicit val optWriter: BSONDocumentWriter[Data[Option]] = deriving.writer[Data, Option]
}

object NestedData
  extends deriving.Fields[NestedData](renaming.snakeCase) with deriving.Writer[NestedData, Ident]
    with deriving.Reader[NestedData, Ident] {
  implicit val optReader: BSONDocumentReader[NestedData[Option]] = deriving.reader[NestedData, Option]
  implicit val optWriter: BSONDocumentWriter[NestedData[Option]] = deriving.writer[NestedData, Option]
}
```

### HKDBSONCollection

All query DSL based on `HKDBSONCollection` which is a wrapper over the original `BSONCollection`. 
To initialize that use `DB.collectionOf[HKD]`, e.g.:

```scala
import mongo.hkd.dsl._
import reactivemongo.api.{AsyncDriver, MongoConnection}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

/* ... */

for {
  uri        <- MongoConnection.fromString("mongodb://localhost:27017/test?connectTimeoutMS=10000")
  connection <- AsyncDriver().connect(uri, Some("test"), strictMode = true)
  database   <- connection.database("test")
} yield database.collectionOf[Data]("collection")
```

### Query DSL

The following extensions are similar to the corresponding MongoDB operations:

| Description                                                                                  | MongoHKD DSL                                                      | Original MongoDB operation    |
|----------------------------------------------------------------------------------------------|-------------------------------------------------------------------|-------------------------------|
| Find all documents in collection                                                             | `collection.findAll().cursor[Ident].collect[List]()`              | `collection.find({})`         |
| Find all documents that satisfies the specified query criteria on the collection             | `collection.findQuery(/* query */).cursor[Ident].collect[List]()` | `collection.find(<query>)`    |
| Find one document (optional) that satisfies the specified query criteria on the collection   | `collection.findQuery(/* query */).one[Ident]`                    | `collection.findOne(<query>)` |
| Find first document (required) that satisfies the specified query criteria on the collection | `collection.findQuery(/* query */).requireOne[Ident]`             | `collection.findOne(<query>)` |

The operation can be additionally specified with `projection` and `sort`.

A detailed example can be found in [QueryDslTest](modules/deriving/src/test/scala/mongo/hkd/QueryDslTest.scala).

### Insert DSL

The following extensions are similar to the corresponding MongoDB operations:

| Description                                    | MongoHKD DSL                                        | Original MongoDB operation                             |
|------------------------------------------------|-----------------------------------------------------|--------------------------------------------------------|
| Inserts a document into the collection         | `collection.insert[Ident].one(document)`            | `collection.insertOne(<document>)`                     |
| Inserts multiple documents into the collection | `collection.insert,many(document1, document2, ...)` | `collection.insertMany(<document1>, <document2>, ...)` |

The operation can be specified with `ordered`, `writeConcern` and `bypassDocumentValidation` parameters.

Examples can be found in [QueryDslTest](modules/deriving/src/test/scala/mongo/hkd/QueryDslTest.scala) and [UpdateDslTest](modules/deriving/src/test/scala/mongo/hkd/UpdateDslTest.scala).

### Update DSL

The following extensions are similar to the corresponding MongoDB operations:

| Description                                                              | MongoHKD DSL                                                | Original MongoDB operation                        |
|--------------------------------------------------------------------------|-------------------------------------------------------------|---------------------------------------------------|
| Updates a single document within the collection based on the filter      | `collection.update.one(/* query */, /* update */)`          | `collection.updateOne(<query>, <update>)`         |
| Updates all documents that match the specified filter for the collection | `collection.update.many(/* query */, /* update */)`         | `collection.updateMany(<query>, <update>)`        |
| Performs multiple write operations with controls for order of execution  | `collection.update.bulk(/* update1 */, /* update2 */, ...)` | `collection.bulkWrite(<update1>, <update2>, ...)` |

The operation can be specified with `ordered`, `writeConcern`, `bypassDocumentValidation`, `upsert`, `collation` and `arrayFilters` parameters.

A detailed example can be found in [UpdateDslTest](modules/deriving/src/test/scala/mongo/hkd/UpdateDslTest.scala).

### Delete DSL

The following extensions are similar to the corresponding MongoDB operations:

| Description                                                              | MongoHKD DSL                                                | Original MongoDB operation                        |
|--------------------------------------------------------------------------|-------------------------------------------------------------|---------------------------------------------------|
| Removes a single document from a collection                              | `collection.delete.one(/* query */)`                        | `collection.deleteOne(<query>)`                   |
| Removes all documents that match the filter from a collection            | `collection.delete.many(/* query */)`                       | `collection.deleteMany(<query>)`                  |
| Performs multiple delete operations with controls for order of execution | `collection.delete.bulk(/* delete1 */, /* delete2 */, ...)` | `collection.bulkWrite(<delete1>, <delete2>, ...)` |

The operation can be specified with `ordered`, `writeConcern` and `collation` parameters.

A detailed example can be found in [DeleteDslTest](modules/deriving/src/test/scala/mongo/hkd/DeleteDslTest.scala).

### Index DSL

The library provides operation `collection.ensureIndices(/* index1 */, /* index2 */, ...)` which corresponds to a batch of `collection.ensureIndex()` operations. 

A detailed example can be found in [IndexDslTest](modules/deriving/src/test/scala/mongo/hkd/IndexDslTest.scala).

### TO DO

1. Scala 3 support (depends on ReactiveMongo status)
2. Support for all MongoDB collection methods
3. More type-safe projections, e.g. projection to a separate data class with type checking
4. etc.
