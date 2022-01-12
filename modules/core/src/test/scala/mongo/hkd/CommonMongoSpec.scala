package mongo.hkd

import com.dimafeng.testcontainers.{ForAllTestContainer, MongoDBContainer}
import mongo.hkd.dsl._
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.testcontainers.utility.DockerImageName
import reactivemongo.api.{AsyncDriver, MongoConnection}

import scala.concurrent.{ExecutionContext, Future}

abstract class CommonMongoSpec extends AsyncFreeSpec with Matchers with ForAllTestContainer {

  override implicit def executionContext: ExecutionContext = ExecutionContext.global

  override val container: MongoDBContainer = MongoDBContainer(DockerImageName.parse("mongo:5.0.3"))

  class CollectionApply[HKD[f[_]]](collection: Future[HKDBSONCollection[HKD]]) {
    def apply[A](body: HKDBSONCollection[HKD] => Future[A]): Future[A] = collection.flatMap(body)
  }

  def withCollection[HKD[f[_]]](implicit fields: BSONField.Fields[HKD]): CollectionApply[HKD] =
    new CollectionApply(
      for {
        uri        <- MongoConnection.fromString(container.replicaSetUrl + "?connectTimeoutMS=10000")
        _           = Thread.sleep(200)
        connection <- AsyncDriver().connect(uri, Some("test"), strictMode = true)
        database   <- connection.database("test")
      } yield database.collectionOf[HKD]("collection")
    )

}
