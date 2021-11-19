package mongo

package object hkd {
  type Ident[A] = A
  def ident[A](a: A): Ident[A] = a
}
