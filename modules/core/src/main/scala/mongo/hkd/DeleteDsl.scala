package mongo.hkd

import mongo.hkd.Record.RecordFields
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.ext.*
import reactivemongo.api.{Collation, WriteConcern}

import scala.concurrent.{ExecutionContext, Future}

sealed abstract class DeleteLimit(val value: Int) {
  case object All extends DeleteLimit(0)
  case object One extends DeleteLimit(1)
}

sealed trait DeleteOp {
  def query: Query

  def limit: Option[DeleteLimit]
  def limit(l: DeleteLimit): DeleteOp

  def collation: Option[Collation]
  def collation(c: Collation): DeleteOp
}

object DeleteOp {
  private case class Impl(
      override val query: Query,
      override val limit: Option[DeleteLimit],
      override val collation: Option[Collation]
  ) extends DeleteOp {
    override def limit(l: DeleteLimit): DeleteOp   = copy(limit = Some(l))
    override def collation(c: Collation): DeleteOp = copy(collation = Some(c))
  }

  def apply(query: Query, limit: Option[DeleteLimit], collation: Option[Collation]): DeleteOp =
    Impl(query, limit, collation)
}

sealed trait DeleteOpBuilder[Data[f[_]]] {
  def op(
      query: Record.RecordFields[Data] => Query,
      limit: Option[DeleteLimit] = None,
      collation: Option[Collation] = None
  ): DeleteOp
}

final class DeleteOperations[Data[f[_]]](
    private val coll: BSONCollection,
    private val fields: Record.RecordFields[Data]
)(
    val ordered: Boolean = true,
    val writeConcern: WriteConcern = coll.db.connection.opts.writeConcern
) extends DeleteOpBuilder[Data] {

  private def builder = coll.delete(ordered = ordered, writeConcern = writeConcern)

  private def element(op: DeleteOp) = builder.element(
    q = op.query,
    limit = op.limit.map(_.value),
    collation = op.collation
  )

  override def op(
      query: RecordFields[Data] => Query,
      limit: Option[DeleteLimit],
      collation: Option[Collation]
  ): DeleteOp =
    DeleteOp(query = query(fields), limit = limit, collation = collation)

  def one(query: Record.RecordFields[Data] => Query, collation: Option[Collation] = None)(implicit
      ec: ExecutionContext
  ): Future[WriteResult] = builder.one(q = query(fields), limit = Some(1), collation = collation)

  def many(query: Record.RecordFields[Data] => Query, collation: Option[Collation] = None)(implicit
      ec: ExecutionContext
  ): Future[WriteResult] = builder.one(q = query(fields), limit = Some(0), collation = collation)

  def bulk(op: DeleteOpBuilder[Data] => DeleteOp, ops: (DeleteOpBuilder[Data] => DeleteOp)*)(implicit
      ec: ExecutionContext
  ): Future[BSONCollection#MultiBulkWriteResult] =
    Future.sequence((op +: ops).map(x => element(x(this)))).flatMap(builder.many)
}

trait DeleteDsl {

  implicit class CollectionDeleteOperations[Data[f[_]]](private val collection: HKDBSONCollection[Data]) {
    def delete: DeleteOperations[Data]                                               =
      collection.delegate(new DeleteOperations(_, collection.fields)(ordered = false))
    def delete(ordered: Boolean): DeleteOperations[Data]                             =
      collection.delegate(new DeleteOperations(_, collection.fields)(ordered = ordered))
    def delete(writeConcern: WriteConcern): DeleteOperations[Data]                   =
      collection.delegate(new DeleteOperations(_, collection.fields)(writeConcern = writeConcern))
    def delete(ordered: Boolean, writeConcern: WriteConcern): DeleteOperations[Data] =
      collection.delegate(new DeleteOperations(_, collection.fields)(ordered = ordered, writeConcern = writeConcern))
  }

}
