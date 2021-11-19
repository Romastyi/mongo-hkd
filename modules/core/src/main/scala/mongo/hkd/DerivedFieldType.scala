package mongo.hkd

import reactivemongo.api.bson._

import scala.annotation.nowarn
import scala.collection.Factory

sealed trait DerivedFieldType[A, T, +E]

@nowarn("msg=is never used")
object DerivedFieldType extends DerivedFieldTypeLowPriorityImplicits {

  sealed trait SimpleField
  sealed trait OptionField
  sealed trait ArrayField

  type Field[A, T]           = DerivedFieldType[A, T, SimpleField]
  type Array[A, T]           = DerivedFieldType[A, T, ArrayField with SimpleField]
  type Option[A, T]          = DerivedFieldType[A, T, OptionField with SimpleField]
  type Nested[A, Data[f[_]]] = DerivedFieldType[A, Data[BSONField], SimpleField]

  def apply[A, T, E](): DerivedFieldType[A, T, E] = null
  def field[A](): Field[A, A]                     = null
  def array[A, T](): Array[A, T]                  = null
  def option[A, T](): Option[A, T]                = null

  implicit def derivedOption[A, T, E](implicit
      t: DerivedFieldType[A, T, E]
  ): DerivedFieldType[scala.Option[A], T, OptionField with E] = null
  implicit def derivedArray[A, M[_], T, E](implicit
      t: DerivedFieldType[A, T, E],
      f: Factory[A, M[A]]
  ): DerivedFieldType[M[A], T, ArrayField with E]             = null
  implicit def derivedNested[Data[f[_]]](implicit
      fs: BSONField.Fields[Data]
  ): DerivedFieldType.Nested[Data[BSONField], Data]           = null
}

@nowarn("msg=is never used")
sealed trait DerivedFieldTypeLowPriorityImplicits {
  implicit def derivedProduct[A, Repr <: Product](implicit ev: Repr ¬ Option[A]): DerivedFieldType.Field[Repr, Repr] =
    null
  implicit def derivedAnyVal[A <: AnyVal]: DerivedFieldType.Field[A, A]                                              = null
  implicit def derivedField[A: BSONWriter]: DerivedFieldType.Field[A, A]                                             = null
}
