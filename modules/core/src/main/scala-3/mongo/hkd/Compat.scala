package mongo.hkd

trait BSONFieldCompat[A] { self: BSONField[A] =>
  inline def /[Data[f[_]]](implicit
      w: DerivedFieldType.Nested[A, Data],
      nested: BSONField.Fields[Data]
  ): BSONField.Fields[Data] = deriving.nested(self, nested)
}

trait RecordCompat {
  type RecordFields[Data[f[_]]] = BSONField.Fields[[F[_]] =>> Record.AsRecord[Data, F]]
}
