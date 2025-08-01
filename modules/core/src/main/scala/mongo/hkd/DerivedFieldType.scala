package mongo.hkd

import reactivemongo.api.bson.*

import scala.collection.Factory

sealed trait DerivedFieldType[A, T, +E]

object DerivedFieldType extends DerivedFieldTypeLowPriorityImplicits with Compat {

  sealed trait SimpleField
  sealed trait OptionField
  sealed trait ArrayField

  type Field[A, T]           = DerivedFieldType[A, T, SimpleField]
  type Array[A, T]           = DerivedFieldType[A, T, ArrayField & SimpleField]
  type Option[A, T]          = DerivedFieldType[A, T, OptionField & SimpleField]
  type Nested[A, Data[f[_]]] = DerivedFieldType[A, Data[BSONField], SimpleField]

  def apply[A, T, E](): DerivedFieldType[A, T, E] = null
  def field[A](): Field[A, A]                     = null
  def array[A, T](): Array[A, T]                  = null
  def option[A, T](): Option[A, T]                = null

  implicit def derivedOption[A, T, E](implicit
      t: DerivedFieldType[A, T, E]
  ): DerivedFieldType[scala.Option[A], T, OptionField & E] = null
  implicit def derivedArray[A, M[_], T, E](implicit
      t: DerivedFieldType[A, T, E],
      f: Factory[A, M[A]]
  ): DerivedFieldType[M[A], T, ArrayField & E]             = null
  implicit def derivedNested[Data[f[_]]](implicit
      fs: BSONField.Fields[Data]
  ): DerivedFieldType.Nested[Data[BSONField], Data]        = null
}

sealed trait DerivedFieldTypeLowPriorityImplicits {
  implicit def derivedProduct[A, Repr <: Product](implicit ev: Repr ¬ Option[A]): DerivedFieldType.Field[Repr, Repr] =
    null
  implicit def derivedAnyVal[A <: AnyVal]: DerivedFieldType.Field[A, A]                                              = null
  implicit def derivedField[A: BSONWriter]: DerivedFieldType.Field[A, A]                                             = null
}
