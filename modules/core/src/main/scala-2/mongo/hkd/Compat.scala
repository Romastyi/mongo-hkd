package mongo.hkd

trait RecordCompat {
  type RecordFields[Data[f[_]]] = BSONField.Fields[Record.AsRecord[Data, *[_]]]
}
