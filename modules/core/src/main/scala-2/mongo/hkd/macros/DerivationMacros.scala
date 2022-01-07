package mongo.hkd.macros

import mongo.hkd.BSONField
import reactivemongo.api.bson.{BSONDocumentReader, BSONDocumentWriter}

import scala.reflect.macros.blackbox

class DerivationMacros(val c: blackbox.Context) {

  import c.universe._

  type WTTF[F[_]] = WeakTypeTag[F[Unit]]

  private def debug(t: Tree): Tree = {
//    println(t)
    t
  }

  private case class CaseClassApply(tpe: Type, in: Type) {
    def apply[A](f: (Symbol, Int) => A): List[A] = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor && m.isPublic && !m.isAbstract =>
        m.typeSignatureIn(in).paramLists.flatten.zipWithIndex.map(f.tupled)
    }.getOrElse(
      c.abort(
        c.enclosingPosition,
        s"Could not identify primary constructor for $tpe"
      )
    )
  }

  private def collectCaseClassFields[Data[f[_]], F[_]: WTTF](implicit w: WeakTypeTag[Data[Any]]): CaseClassApply =
    CaseClassApply(w.tpe, appliedType(w.tpe.typeConstructor, weakTypeOf[F[Unit]].typeConstructor))

  def fieldsImpl[Data[f[_]]](
      naming: c.Expr[String => String]
  )(implicit w: c.WeakTypeTag[Data[Any]]): c.Expr[BSONField.Fields[Data]] = {
    val dt     = weakTypeOf[Data[Any]].typeConstructor
    val fields = collectCaseClassFields[Data, BSONField].apply { case (sym, _) =>
      q"""BSONField($naming(${sym.name.decodedName.toString}))"""
    }

    c.Expr[BSONField.Fields[Data]](
      debug(q"""new $dt(..$fields)""")
    )
  }

  def fieldsImplWithNamingResolver[Data[f[_]]](
      resolver: c.Expr[NamingResolver[Data]]
  )(implicit w: c.WeakTypeTag[Data[Any]]): c.Expr[BSONField.Fields[Data]] =
    fieldsImpl(resolver)

  def readerImpl[Data[f[_]], F[_]: WTTF](
      fields: c.Expr[BSONField.Fields[Data]]
  )(implicit w: c.WeakTypeTag[Data[Any]]): c.Expr[BSONDocumentReader[Data[F]]] = {
    val ft        = weakTypeOf[F[Unit]].typeConstructor
    val dt        = weakTypeOf[Data[Any]].typeConstructor
    val fs        = collectCaseClassFields[Data, F].apply { case (sym, idx) =>
      val fieldType = sym.typeSignature
      val fieldName = sym.name.decodedName.toTermName
      q"""implicitly[BSONReader[$fieldType]]""" -> q"""$fieldName = fieldValues($idx).asInstanceOf[$fieldType]"""
    }
    val readers   = fs.map(_._1)
    val rawParams = fs.map(_._2)

    c.Expr[BSONDocumentReader[Data[F]]] {
      debug(
        q"""
           new DerivedBSONReader[$dt, $ft]($fields, List(..$readers), { fieldValues => new $dt(..$rawParams) })
         """
      )
    }
  }

  def writerImpl[Data[f[_]], F[_]: WTTF](
      fields: c.Expr[BSONField.Fields[Data]]
  )(implicit w: c.WeakTypeTag[Data[Any]]): c.Expr[BSONDocumentWriter[Data[F]]] = {
    val ft      = weakTypeOf[F[Unit]].typeConstructor
    val dt      = weakTypeOf[Data[Any]].typeConstructor
    val writers = collectCaseClassFields[Data, F].apply { case (sym, _) =>
      q"""implicitly[BSONWriter[${sym.typeSignature}]]"""
    }

    c.Expr[BSONDocumentWriter[Data[F]]] {
      debug(
        q"""
           new DerivedBSONWriter[$dt, $ft]($fields, List(..$writers))
         """
      )
    }
  }

}

final case class NamingResolver[Data[f[_]]] private (naming: String => String) extends (String => String) {
  override def apply(v1: String): String = naming(v1)
}
