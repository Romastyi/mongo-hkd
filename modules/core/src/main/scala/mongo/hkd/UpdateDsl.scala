package mongo.hkd

import reactivemongo.api.WriteConcern
import reactivemongo.api.bson._
import reactivemongo.api.bson.collection.BSONCollection

import scala.annotation.nowarn
import scala.concurrent.{ExecutionContext, Future}

final case class UpdateOperations[Data[f[_]]](
    private val builder: BSONCollection#UpdateBuilder,
    private val fields: Record.RecordFields[Data]
) {
  def one(
      query: Record.RecordFields[Data] => Query,
      op: Record.RecordFields[Data] => FieldUpdateOperator,
      ops: (Record.RecordFields[Data] => FieldUpdateOperator)*
  )(implicit ec: ExecutionContext): Future[BSONCollection#UpdateWriteResult] =
    builder.one(query(fields), UpdateOperators((op +: ops).map(_.apply(fields))))

  def many(
      query: Record.RecordFields[Data] => Query,
      op: Record.RecordFields[Data] => FieldUpdateOperator,
      ops: (Record.RecordFields[Data] => FieldUpdateOperator)*
  )(implicit ec: ExecutionContext): Future[BSONCollection#UpdateWriteResult] =
    builder.one(query(fields), UpdateOperators((op +: ops).map(_.apply(fields))), multi = true)
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
}

object UpdateOperators {
  def empty: UpdateOperators                                = UpdateOperators(Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil)
  def apply(ops: Seq[FieldUpdateOperator]): UpdateOperators = ops.foldLeft(empty)(_ add _)

  implicit def `BSONWriter[UpdateOperators]` : BSONDocumentWriter[UpdateOperators] = BSONDocumentWriter { op =>
    def e(name: String, s: Seq[FieldUpdateOperator]): ElementProducer = name -> Some(s).filter(_.nonEmpty)
    document(
      e("$currentDate", op.$currentDate),
      e("$inc", op.$inc),
      e("$min", op.$min),
      e("$max", op.$max),
      e("$mul", op.$mul),
      e("$rename", op.$rename),
      e("$set", op.$set),
      e("$setOrInsert", op.$setOrInsert),
      e("$unset", op.$unset)
    )
  }
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
      UpdateOperations(collection.delegate(_.update), collection.fields)
    def update(ordered: Boolean): UpdateOperations[Data]                                    =
      UpdateOperations(collection.delegate(_.update(ordered)), collection.fields)
    def update(writeConcern: WriteConcern): UpdateOperations[Data]                          =
      UpdateOperations(collection.delegate(_.update(writeConcern)), collection.fields)
    def update(ordered: Boolean, writeConcern: WriteConcern): UpdateOperations[Data]        =
      UpdateOperations(collection.delegate(_.update(ordered, writeConcern)), collection.fields)
    def update(ordered: Boolean, bypassDocumentValidation: Boolean): UpdateOperations[Data] =
      UpdateOperations(collection.delegate(_.update(ordered, bypassDocumentValidation)), collection.fields)
    def update(
        ordered: Boolean,
        writeConcern: WriteConcern,
        bypassDocumentValidation: Boolean
    ): UpdateOperations[Data]                                                               =
      UpdateOperations(
        collection.delegate(_.update(ordered, writeConcern, bypassDocumentValidation)),
        collection.fields
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
