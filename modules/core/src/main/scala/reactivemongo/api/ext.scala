package reactivemongo.api

object ext {
  implicit class MongoConnectionOps(val connection: MongoConnection) extends AnyVal {
    def opts: MongoConnectionOptions = connection.options
  }
}
