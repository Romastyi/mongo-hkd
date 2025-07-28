package mongo.hkd

import mongo.hkd.macros.DerivationMacros

trait BSONFieldCompat[A] { self: BSONField[A] =>
  def /[Data[f[_]]](implicit
      w: DerivedFieldType.Nested[A, Data],
      nested: BSONField.Fields[Data]
  ): BSONField.Fields[Data] =
    macro DerivationMacros.nestedImpl[A, Data]
}

trait RecordCompat extends Compat {
  type RecordFields[Data[f[_]]] = BSONField.Fields[Record.AsRecord[Data, *[_]]]
}

trait Compat {
  type &[A, B] = A with B
}
