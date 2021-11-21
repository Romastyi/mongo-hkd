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

  def op(
      query: Record.RecordFields[Data] => Query,
      update: UpdateOperatorsHKD[Data] => UpdateOperatorsHKD[Data],
      multi: Boolean = false,
      upsert: Boolean = false,
      collation: Option[Collation] = None,
      arrayFilters: Seq[BSONDocument] = Nil
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

  override def op(
      query: RecordFields[Data] => Query,
      update: UpdateOperatorsHKD[Data] => UpdateOperatorsHKD[Data],
      multi: Boolean,
      upsert: Boolean,
      collation: Option[Collation],
      arrayFilters: Seq[BSONDocument]
  ): UpdateOp = UpdateOp(
    query = query(fields),
    ops = update(UpdateOperators(fields)),
    upsert = upsert,
    multi = multi,
    collation = collation,
    arrayFilters = arrayFilters
  )

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

  def one(
      query: Record.RecordFields[Data] => Query,
      update: UpdateOperatorsHKD[Data] => UpdateOperatorsHKD[Data],
      upsert: Boolean = false,
      collation: Option[Collation] = None,
      arrayFilters: Seq[BSONDocument] = Nil
  )(implicit ec: ExecutionContext): Future[BSONCollection#UpdateWriteResult] = execute(
    UpdateOp(
      query = query(fields),
      ops = update(UpdateOperators(fields)),
      upsert = upsert,
      multi = false,
      collation = collation,
      arrayFilters = arrayFilters
    )
  )

  def many(update: UpdateOpBuilder[Data] => UpdateOp)(implicit
      ec: ExecutionContext
  ): Future[BSONCollection#UpdateWriteResult] =
    execute(update(this).multi(true))

  def many(
      query: Record.RecordFields[Data] => Query,
      update: UpdateOperatorsHKD[Data] => UpdateOperatorsHKD[Data],
      upsert: Boolean = false,
      collation: Option[Collation] = None,
      arrayFilters: Seq[BSONDocument] = Nil
  )(implicit ec: ExecutionContext): Future[BSONCollection#UpdateWriteResult] = execute(
    UpdateOp(
      query = query(fields),
      ops = update(UpdateOperators(fields)),
      upsert = upsert,
      multi = true,
      collation = collation,
      arrayFilters = arrayFilters
    )
  )

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
  def apply(
      query: Query,
      ops: UpdateOperators,
      upsert: Boolean,
      multi: Boolean,
      collation: Option[Collation],
      arrayFilters: Seq[BSONDocument]
  ): UpdateOp                                             = Impl(query, ops, upsert = upsert, multi = multi, collation, arrayFilters)
}

sealed trait UpdateOperators {
  def $currentDate: Seq[FieldUpdateOperator.CurrentDate[_]]
  def $inc: Seq[FieldUpdateOperator.Inc[_]]
  def $min: Seq[FieldUpdateOperator.Min[_]]
  def $max: Seq[FieldUpdateOperator.Max[_]]
  def $mul: Seq[FieldUpdateOperator.Mul[_]]
  def $rename: Seq[FieldUpdateOperator.Rename[_]]
  def $set: Seq[FieldUpdateOperator.Set[_]]
  def $setOrInsert: Seq[FieldUpdateOperator.SetOrInsert[_]]
  def $unset: Seq[FieldUpdateOperator.Unset[_]]

  def add(op: FieldUpdateOperator): UpdateOperators

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

sealed trait UpdateOperatorsHKD[Data[f[_]]] extends UpdateOperators {
  def fields: Record.RecordFields[Data]

  def $currentDate(fs: (Record.RecordFields[Data] => FieldUpdateOperator.CurrentDate[_])*): UpdateOperatorsHKD[Data]
  def $inc(fs: (Record.RecordFields[Data] => FieldUpdateOperator.Inc[_])*): UpdateOperatorsHKD[Data]
  def $min(fs: (Record.RecordFields[Data] => FieldUpdateOperator.Min[_])*): UpdateOperatorsHKD[Data]
  def $max(fs: (Record.RecordFields[Data] => FieldUpdateOperator.Max[_])*): UpdateOperatorsHKD[Data]
  def $mul(fs: (Record.RecordFields[Data] => FieldUpdateOperator.Mul[_])*): UpdateOperatorsHKD[Data]
  def $rename(fs: (Record.RecordFields[Data] => FieldUpdateOperator.Rename[_])*): UpdateOperatorsHKD[Data]
  def $set(fs: (Record.RecordFields[Data] => FieldUpdateOperator.Set[_])*): UpdateOperatorsHKD[Data]
  def $setOrInsert(fs: (Record.RecordFields[Data] => FieldUpdateOperator.SetOrInsert[_])*): UpdateOperatorsHKD[Data]
  def $unset(fs: (Record.RecordFields[Data] => FieldUpdateOperator.Unset[_])*): UpdateOperatorsHKD[Data]
}

object UpdateOperators {
  private case class Impl(
      override val $currentDate: Seq[FieldUpdateOperator.CurrentDate[_]],
      override val $inc: Seq[FieldUpdateOperator.Inc[_]],
      override val $min: Seq[FieldUpdateOperator.Min[_]],
      override val $max: Seq[FieldUpdateOperator.Max[_]],
      override val $mul: Seq[FieldUpdateOperator.Mul[_]],
      override val $rename: Seq[FieldUpdateOperator.Rename[_]],
      override val $set: Seq[FieldUpdateOperator.Set[_]],
      override val $setOrInsert: Seq[FieldUpdateOperator.SetOrInsert[_]],
      override val $unset: Seq[FieldUpdateOperator.Unset[_]]
  ) extends UpdateOperators {
    override def add(op: FieldUpdateOperator): UpdateOperators = op match {
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
  }

  final case class ImplHKD[Data[f[_]]](
      override val fields: Record.RecordFields[Data],
      override val $currentDate: Seq[FieldUpdateOperator.CurrentDate[_]],
      override val $inc: Seq[FieldUpdateOperator.Inc[_]],
      override val $min: Seq[FieldUpdateOperator.Min[_]],
      override val $max: Seq[FieldUpdateOperator.Max[_]],
      override val $mul: Seq[FieldUpdateOperator.Mul[_]],
      override val $rename: Seq[FieldUpdateOperator.Rename[_]],
      override val $set: Seq[FieldUpdateOperator.Set[_]],
      override val $setOrInsert: Seq[FieldUpdateOperator.SetOrInsert[_]],
      override val $unset: Seq[FieldUpdateOperator.Unset[_]]
  ) extends UpdateOperatorsHKD[Data] {
    override def add(op: FieldUpdateOperator): UpdateOperators = op match {
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

    def add(fs: Seq[Record.RecordFields[Data] => FieldUpdateOperator]): UpdateOperatorsHKD[Data] =
      fs.map(_.apply(fields)).foldLeft(this: UpdateOperators)(_ add _).asInstanceOf[UpdateOperatorsHKD[Data]]

    def $currentDate(fs: (Record.RecordFields[Data] => FieldUpdateOperator.CurrentDate[_])*): UpdateOperatorsHKD[Data] =
      add(fs)
    def $inc(fs: (Record.RecordFields[Data] => FieldUpdateOperator.Inc[_])*): UpdateOperatorsHKD[Data]                 =
      add(fs)
    def $min(fs: (Record.RecordFields[Data] => FieldUpdateOperator.Min[_])*): UpdateOperatorsHKD[Data]                 =
      add(fs)
    def $max(fs: (Record.RecordFields[Data] => FieldUpdateOperator.Max[_])*): UpdateOperatorsHKD[Data]                 =
      add(fs)
    def $mul(fs: (Record.RecordFields[Data] => FieldUpdateOperator.Mul[_])*): UpdateOperatorsHKD[Data]                 =
      add(fs)
    def $rename(fs: (Record.RecordFields[Data] => FieldUpdateOperator.Rename[_])*): UpdateOperatorsHKD[Data]           =
      add(fs)
    def $set(fs: (Record.RecordFields[Data] => FieldUpdateOperator.Set[_])*): UpdateOperatorsHKD[Data]                 =
      add(fs)
    def $setOrInsert(fs: (Record.RecordFields[Data] => FieldUpdateOperator.SetOrInsert[_])*): UpdateOperatorsHKD[Data] =
      add(fs)
    def $unset(fs: (Record.RecordFields[Data] => FieldUpdateOperator.Unset[_])*): UpdateOperatorsHKD[Data]             =
      add(fs)
  }

  def empty: UpdateOperators                                                         = Impl(Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil)
  def apply(ops: Seq[FieldUpdateOperator]): UpdateOperators                          = ops.foldLeft(empty)(_ add _)
  def apply[Data[f[_]]](fields: Record.RecordFields[Data]): UpdateOperatorsHKD[Data] =
    ImplHKD(fields, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil)

  implicit def `BSONWriter[UpdateOperators]`[U <: UpdateOperators]: BSONDocumentWriter[U] = BSONDocumentWriter(_.bson)
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

@nowarn("msg=is never used")
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

  implicit def incField[A, T](p: (BSONField[A], T))(implicit
      t: DerivedFieldType.Field[A, T],
      w: BSONWriter[T],
      n: Numeric[T]
  ): FieldUpdateOperator.Inc[A]                                                                         =
    FieldUpdateOperator.Inc(p._1, p._2)
  implicit def mulField[A, T](p: (BSONField[A], T))(implicit
      t: DerivedFieldType.Field[A, T],
      w: BSONWriter[T],
      n: Numeric[T]
  ): FieldUpdateOperator.Mul[A]                                                                         =
    FieldUpdateOperator.Mul(p._1, p._2)
  implicit def maxField[A](p: (BSONField[A], A))(implicit w: BSONWriter[A]): FieldUpdateOperator.Max[A] =
    FieldUpdateOperator.Max(p._1, p._2)
  implicit def minField[A](p: (BSONField[A], A))(implicit w: BSONWriter[A]): FieldUpdateOperator.Min[A] =
    FieldUpdateOperator.Min(p._1, p._2)
  implicit def renameField[A](p: (BSONField[A], String)): FieldUpdateOperator.Rename[A]                 =
    FieldUpdateOperator.Rename(p._1, p._2)
  implicit def setField[A](p: (BSONField[A], A))(implicit w: BSONWriter[A]): FieldUpdateOperator.Set[A] =
    FieldUpdateOperator.Set(p._1, p._2)
  implicit def setOrInsertField[A](p: (BSONField[A], A))(implicit
      w: BSONWriter[A]
  ): FieldUpdateOperator.SetOrInsert[A]                                                                 =
    FieldUpdateOperator.SetOrInsert(p._1, p._2)
  implicit def unsetField[A](f: BSONField[A]): FieldUpdateOperator.Unset[A]                             =
    FieldUpdateOperator.Unset(f)

}
