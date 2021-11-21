package mongo.hkd

import mongo.hkd.Record.RecordFields
import reactivemongo.api.bson._
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.{Collation, WriteConcern}
import reactivemongo.api.ext._

import scala.annotation.nowarn
import scala.concurrent.{ExecutionContext, Future}

sealed trait UpdateOpBuilder[Data[f[_]]] {
  def op(query: Record.RecordFields[Data] => Query)(
      op: Record.RecordFields[Data] => FieldUpdateOperator,
      ops: (Record.RecordFields[Data] => FieldUpdateOperator)*
  ): UpdateOp
}

final class UpdateOperations[Data[f[_]]](
    private val coll: BSONCollection,
    private val fields: Record.RecordFields[Data]
)(
    val ordered: Boolean = false,
    val writeConcern: WriteConcern = coll.db.connection.opts.writeConcern,
    val bypassDocumentValidation: Boolean = false,
) extends UpdateOpBuilder[Data] {

  override def op(query: RecordFields[Data] => Query)(
      op: RecordFields[Data] => FieldUpdateOperator,
      ops: (RecordFields[Data] => FieldUpdateOperator)*
  ): UpdateOp =
    UpdateOp(query(fields), UpdateOperators((op +: ops).map(_.apply(fields))))

  private def builder = coll.update(ordered, writeConcern, bypassDocumentValidation)

  private def element(op: UpdateOp) = builder.element(
    q = op.query.bson,
    u = op.ops.bson,
    upsert = op.upsert,
    multi = op.multi,
    collation = op.collation,
    arrayFilters = op.arrayFilters
  )

  private def execute(op: UpdateOp)(implicit ec: ExecutionContext): Future[BSONCollection#UpdateWriteResult] =
    builder.one(
      q = op.query,
      u = op.ops,
      upsert = op.upsert,
      multi = op.multi,
      collation = op.collation,
      arrayFilters = op.arrayFilters
    )

  def one(update: UpdateOpBuilder[Data] => UpdateOp)(implicit
      ec: ExecutionContext
  ): Future[BSONCollection#UpdateWriteResult] = execute(update(this).multi(false))

  def many(update: UpdateOpBuilder[Data] => UpdateOp)(implicit
      ec: ExecutionContext
  ): Future[BSONCollection#UpdateWriteResult] =
    execute(update(this).multi(true))

  def bulk(
      update: UpdateOpBuilder[Data] => UpdateOp,
      updates: (UpdateOpBuilder[Data] => UpdateOp)*
  )(implicit ec: ExecutionContext): Future[BSONCollection#MultiBulkWriteResult] =
    Future
      .sequence((update +: updates).map(op => element(op(this))))
      .flatMap(builder.many)
}

sealed trait UpdateOp {
  def query: Query
  def ops: UpdateOperators

  def multi: Boolean
  def multi(b: Boolean): UpdateOp

  def upsert: Boolean
  def upsert(b: Boolean): UpdateOp

  def collation: Option[Collation]
  def collation(c: Collation): UpdateOp

  def arrayFilters: Seq[BSONDocument]
  def arrayFilters(f: BSONDocument, fs: BSONDocument*): UpdateOp
}

object UpdateOp {
  private case class Impl(
      override val query: Query,
      override val ops: UpdateOperators,
      override val upsert: Boolean,
      override val multi: Boolean,
      override val collation: Option[Collation],
      override val arrayFilters: Seq[BSONDocument]
  ) extends UpdateOp {
    override def multi(b: Boolean): UpdateOp                                = copy(multi = b)
    override def upsert(b: Boolean): UpdateOp                               = copy(upsert = b)
    override def collation(c: Collation): UpdateOp                          = copy(collation = Some(c))
    override def arrayFilters(f: BSONDocument, fs: BSONDocument*): UpdateOp = copy(arrayFilters = f +: fs)
  }

  def apply(query: Query, ops: UpdateOperators): UpdateOp = Impl(query, ops, upsert = false, multi = false, None, Nil)
}

final case class UpdateOperators private (
    $currentDate: Seq[FieldUpdateOperator.CurrentDate[_]],
    $inc: Seq[FieldUpdateOperator.Inc[_]],
    $min: Seq[FieldUpdateOperator.Min[_]],
    $max: Seq[FieldUpdateOperator.Max[_]],
    $mul: Seq[FieldUpdateOperator.Mul[_]],
    $rename: Seq[FieldUpdateOperator.Rename[_]],
    $set: Seq[FieldUpdateOperator.Set[_]],
    $setOrInsert: Seq[FieldUpdateOperator.SetOrInsert[_]],
    $unset: Seq[FieldUpdateOperator.Unset[_]]
) {
  def add(op: FieldUpdateOperator): UpdateOperators = op match {
    case c: FieldUpdateOperator.CurrentDate[_] => copy($currentDate = $currentDate :+ c)
    case m: FieldUpdateOperator.Inc[_]         => copy($inc = $inc :+ m)
    case m: FieldUpdateOperator.Min[_]         => copy($min = $min :+ m)
    case m: FieldUpdateOperator.Max[_]         => copy($max = $max :+ m)
    case m: FieldUpdateOperator.Mul[_]         => copy($mul = $mul :+ m)
    case r: FieldUpdateOperator.Rename[_]      => copy($rename = $rename :+ r)
    case s: FieldUpdateOperator.Set[_]         => copy($set = $set :+ s)
    case s: FieldUpdateOperator.SetOrInsert[_] => copy($setOrInsert = $setOrInsert :+ s)
    case u: FieldUpdateOperator.Unset[_]       => copy($unset = $unset :+ u)
  }

  def bson: BSONDocument = {
    def e(name: String, s: Seq[FieldUpdateOperator]): ElementProducer = name -> Some(s).filter(_.nonEmpty)
    document(
      e("$currentDate", $currentDate),
      e("$inc", $inc),
      e("$min", $min),
      e("$max", $max),
      e("$mul", $mul),
      e("$rename", $rename),
      e("$set", $set),
      e("$setOrInsert", $setOrInsert),
      e("$unset", $unset)
    )
  }
}

object UpdateOperators {
  def empty: UpdateOperators                                = UpdateOperators(Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil)
  def apply(ops: Seq[FieldUpdateOperator]): UpdateOperators = ops.foldLeft(empty)(_ add _)

  implicit def `BSONWriter[UpdateOperators]` : BSONDocumentWriter[UpdateOperators] = BSONDocumentWriter(_.bson)
}

sealed trait FieldUpdateOperator {
  def bson: ElementProducer
}

object FieldUpdateOperator {
  sealed trait CurrentDateTypeSpecification
  object SetToCurrentDate extends CurrentDateTypeSpecification
  object SetAsTimestamp   extends CurrentDateTypeSpecification
  object SetAsDate        extends CurrentDateTypeSpecification

  @nowarn("msg=is never used")
  object CurrentDateTypeSpecification {
    implicit def setToCurrentDate(b: true): CurrentDateTypeSpecification      = SetToCurrentDate
    implicit def setAsTimestamp(t: "timestamp"): CurrentDateTypeSpecification = SetAsTimestamp
    implicit def setAsDate(t: "date"): CurrentDateTypeSpecification           = SetAsDate
  }

  final case class CurrentDate[A](field: BSONField[A], typeSpecification: CurrentDateTypeSpecification)
      extends FieldUpdateOperator {
    override def bson: ElementProducer = field.fieldName -> (typeSpecification match {
      case SetToCurrentDate => BSONBoolean(true)
      case SetAsTimestamp   => document("$type" -> "timestamp")
      case SetAsDate        => document("$type" -> "date")
    })
  }
  final case class Inc[A](field: BSONField[A], amount: BSONValueWrapper) extends FieldUpdateOperator {
    override def bson: ElementProducer = field.fieldName -> amount.bson
  }
  final case class Min[A](field: BSONField[A], value: BSONValueWrapper) extends FieldUpdateOperator {
    override def bson: ElementProducer = field.fieldName -> value.bson
  }
  final case class Max[A](field: BSONField[A], value: BSONValueWrapper) extends FieldUpdateOperator {
    override def bson: ElementProducer = field.fieldName -> value.bson
  }
  final case class Mul[A](field: BSONField[A], number: BSONValueWrapper) extends FieldUpdateOperator {
    override def bson: ElementProducer = field.fieldName -> number.bson
  }
  final case class Rename[A](field: BSONField[A], newName: String) extends FieldUpdateOperator {
    override def bson: ElementProducer = field.fieldName -> newName
  }
  final case class Set[A](field: BSONField[A], value: BSONValueWrapper) extends FieldUpdateOperator {
    override def bson: ElementProducer = field.fieldName -> value.bson
  }
  final case class SetOrInsert[A](field: BSONField[A], value: BSONValueWrapper) extends FieldUpdateOperator {
    override def bson: ElementProducer = field.fieldName -> value.bson
  }
  final case class Unset[A](field: BSONField[A]) extends FieldUpdateOperator {
    override def bson: ElementProducer = field.fieldName -> "\"\""
  }

  implicit def `BSONWriter[Seq[FieldUpdateOperator]]`[A <: FieldUpdateOperator]: BSONDocumentWriter[Seq[A]] =
    BSONDocumentWriter { ops =>
      document(ops.map(_.bson): _*)
    }
}

trait UpdateDsl {

  implicit class CollectionUpdateOperations[Data[f[_]]](private val collection: HKDBSONCollection[Data]) {
    def update: UpdateOperations[Data]                                                      =
      collection.delegate(new UpdateOperations(_, collection.fields)())
    def update(ordered: Boolean): UpdateOperations[Data]                                    =
      collection.delegate(new UpdateOperations(_, collection.fields)(ordered = ordered))
    def update(writeConcern: WriteConcern): UpdateOperations[Data]                          =
      collection.delegate(new UpdateOperations(_, collection.fields)(writeConcern = writeConcern))
    def update(ordered: Boolean, writeConcern: WriteConcern): UpdateOperations[Data]        =
      collection.delegate(new UpdateOperations(_, collection.fields)(ordered = ordered, writeConcern = writeConcern))
    def update(ordered: Boolean, bypassDocumentValidation: Boolean): UpdateOperations[Data] =
      collection.delegate(
        new UpdateOperations(_, collection.fields)(
          ordered = ordered,
          bypassDocumentValidation = bypassDocumentValidation
        )
      )
    def update(
        ordered: Boolean,
        writeConcern: WriteConcern,
        bypassDocumentValidation: Boolean
    ): UpdateOperations[Data]                                                               =
      collection.delegate(
        new UpdateOperations(_, collection.fields)(
          ordered = ordered,
          writeConcern = writeConcern,
          bypassDocumentValidation = bypassDocumentValidation
        )
      )
  }

  implicit class FieldUpdateOperatorOps[A](field: BSONField[A]) {
    def $currentDate(typeSpec: FieldUpdateOperator.CurrentDateTypeSpecification): FieldUpdateOperator.CurrentDate[A] =
      FieldUpdateOperator.CurrentDate(field, typeSpec)
    def $max(number: A)(implicit w: BSONWriter[A]): FieldUpdateOperator.Max[A]                                       =
      FieldUpdateOperator.Max(field, number)
    def $min(number: A)(implicit w: BSONWriter[A]): FieldUpdateOperator.Min[A]                                       =
      FieldUpdateOperator.Min(field, number)
    def $rename(newName: String): FieldUpdateOperator.Rename[A]                                                      =
      FieldUpdateOperator.Rename(field, newName)
    def $set(value: A)(implicit w: BSONWriter[A]): FieldUpdateOperator.Set[A]                                        =
      FieldUpdateOperator.Set(field, value)
    def $setOrInsert(value: A)(implicit w: BSONWriter[A]): FieldUpdateOperator.SetOrInsert[A]                        =
      FieldUpdateOperator.SetOrInsert(field, value)
    def $unset: FieldUpdateOperator.Unset[A]                                                                         =
      FieldUpdateOperator.Unset(field)
  }

  implicit class NumericFieldUpdateOperatorOps[A, T](field: BSONField[A])(implicit
      t: DerivedFieldType.Field[A, T],
      w: BSONWriter[T],
      n: Numeric[T]
  ) {
    def $inc(number: T): FieldUpdateOperator.Inc[A] = FieldUpdateOperator.Inc(field, number)
    def $mul(number: T): FieldUpdateOperator.Mul[A] = FieldUpdateOperator.Mul(field, number)
  }

}
