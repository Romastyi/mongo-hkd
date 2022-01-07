package mongo.hkd

trait RecordCompat {
  type RecordFields[Data[f[_]]] = BSONField.Fields[[F[_]] =>> Record.AsRecord[Data, F]]
}
