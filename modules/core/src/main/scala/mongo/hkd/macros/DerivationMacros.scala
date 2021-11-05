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

  private case class CaseClass[A](tpe: Type, companion: Symbol, fields: List[List[A]])

  private def collectCaseClassFields[Data[f[_]], A](f: Symbol => A)(implicit
      w: c.WeakTypeTag[Data[Any]]
  ): CaseClass[A] = {
    val tpe       = weakTypeTag[Data[Any]].tpe
    val companion = tpe.typeSymbol.companion
    val fields    = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor && m.isPublic && !m.isAbstract =>
        m.paramLists.map(_.map(f))
    }.getOrElse(
      c.abort(
        c.enclosingPosition,
        s"Could not identify primary constructor for $tpe"
      )
    )
    CaseClass(tpe = tpe, companion = companion, fields = fields)
  }

  def fieldsImpl[Data[f[_]]](
      naming: c.Expr[String => String]
  )(implicit w: c.WeakTypeTag[Data[Any]]): c.Expr[BSONField.Fields[Data]] = {
    val caseClass = collectCaseClassFields { sym =>
      if (sym.annotations.exists(_.tree.tpe =:= typeOf[mongo.hkd.oid])) {
        q"""BSONField("_id")"""
      } else {
        q"""BSONField($naming(${sym.name.decodedName.toString}))"""
      }
    }

    c.Expr[BSONField.Fields[Data]](
      debug(q"""${caseClass.companion}.apply(...${caseClass.fields})""")
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
    val caseClass = collectCaseClassFields { sym =>
      val fieldName = sym.name.decodedName.toTermName
      q"""$fieldName = $fieldName""" -> fq"""$fieldName <- fields.$fieldName.read[$ft](bson)"""
    }
    val params    = caseClass.fields.flatten

    c.Expr[BSONDocumentReader[Data[F]]] {
      debug(
        q"""BSONDocumentReader.from { bson =>
           val fields = $fields
           for (..${params.map(_._2)}) yield ${caseClass.companion}.apply[$ft](..${params.map(_._1)})
         }"""
      )
    }
  }

  def writerImpl[Data[f[_]], F[_]: WTTF](
      fields: c.Expr[BSONField.Fields[Data]]
  )(implicit w: c.WeakTypeTag[Data[Any]]): c.Expr[BSONDocumentWriter[Data[F]]] = {
    val ft        = weakTypeOf[F[Unit]].typeConstructor
    val caseClass = collectCaseClassFields { sym =>
      val fieldName = sym.name.decodedName.toTermName
      fieldName -> fq"""$fieldName <- fields.$fieldName.write[$ft](data.$fieldName)"""
    }
    val params    = caseClass.fields.flatten

    c.Expr[BSONDocumentWriter[Data[F]]] {
      debug(
        q"""BSONDocumentWriter.from { data =>
           val fields = $fields 
           for (..${params.map(_._2)}) yield reactivemongo.api.bson.document(..${params.map(_._1)})
         }"""
      )
    }
  }

}

final case class NamingResolver[Data[f[_]]] private (naming: String => String) extends (String => String) {
  override def apply(v1: String): String = naming(v1)
}
