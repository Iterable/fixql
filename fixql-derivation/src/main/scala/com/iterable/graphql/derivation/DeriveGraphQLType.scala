package com.iterable.graphql.derivation

import graphql.schema.{GraphQLFieldDefinition, GraphQLObjectType, GraphQLOutputType}
import shapeless.{::, HList, HNil, LabelledGeneric, Poly1}
import shapeless.labelled.{FieldType, field}
import shapeless.ops.hlist.ZipWithKeys
import shapeless.ops.record.{SelectAll, ToMap}

object ToGraphQLType extends Poly1 {
  /** Note this could be better done with a Poly0 but using a Poly1 lets me
    * use MapValuesNull. What I really want is "FillValuesWith" - a combination of
    * MapValues and FillWith.
    */
  implicit def toGraphQLType[T](implicit t: IsGraphQLOutputType[T]): Case.Aux[T, GraphQLOutputType] = {
    at[T] { _ => t.graphQLType }
  }
}

object DeriveGraphQLType {
  def derive[T](name: String) = new Derive[T](name)

  class Derive[T](name: String) {
    def allFields[L <: HList, O <: HList, MV <: HList]
    (implicit
     gen: LabelledGeneric.Aux[T, L],
     mapValues: MapValuesNull.Aux[ToGraphQLType.type, L, MV],
     toMap: ToMap.Aux[MV, Symbol, GraphQLOutputType]
    ) = {
      deriveGraphQLObjectType(name)
    }

    /** Only include the selected fields in the generated object type. If you want to
      * customize anything about a field's definition, you should simply define the
      * field manually rather than using automatic derivation.
      */
    def selected[L <: HList, O <: HList, MV <: HList, S <: HList, V <: HList, L2 <: HList]
    (selections: S)
    (implicit
     gen: LabelledGeneric.Aux[T, L],
     select: SelectAll.Aux[L, S, V],
     zipped: ZipWithKeys.Aux[S, V, L2],
     mapValues: MapValuesNull.Aux[ToGraphQLType.type, L2, MV],
     toMap: ToMap.Aux[MV, _, GraphQLOutputType]
    ) = {
      val mv = mapValues.apply()
      val map = toMap.apply(mv)
      createObjectType(name, map.map { case (key, value) => key.asInstanceOf[Symbol] -> value})
    }
  }

  def deriveGraphQLObjectType[T, L <: HList, O <: HList, MV <: HList]
  (typeName: String)
  (implicit
   gen: LabelledGeneric.Aux[T, L],
   mapValues: MapValuesNull.Aux[ToGraphQLType.type, L, MV],
   toMap: ToMap.Aux[MV, Symbol, GraphQLOutputType]
  ): GraphQLObjectType = {
    val mv = mapValues.apply()
    val map = toMap.apply(mv)
    createObjectType(typeName, map)
  }

  private def createObjectType(name: String, map: Map[Symbol, GraphQLOutputType]) = {
    import scala.collection.JavaConverters.seqAsJavaListConverter
    val fieldDefs = map.map { case (name, typ) =>
      GraphQLFieldDefinition.newFieldDefinition()
        .name(name.name)
        .`type`(typ)
        .build
    }
    GraphQLObjectType.newObject()
      .name(name)
      .fields(fieldDefs.toSeq.asJava)
      .build
  }
}

/**
  * Variant of MapValues that doesn't require any values. Instead HF is assumed to rely on the
  * type only.
  */
trait MapValuesNull[HF, L <: HList] extends Serializable { type Out <: HList; def apply(): Out }

object MapValuesNull {
  type Aux[HF, L <: HList, Out0 <: HList] = MapValuesNull[HF, L] { type Out = Out0 }

  implicit def hnilMapValues[HF, L <: HNil]: Aux[HF, L, HNil] =
    new MapValuesNull[HF, L] {
      type Out = HNil
      def apply() = HNil
    }

  implicit def hconsMapValues[HF, K, V, T <: HList](implicit
                                                    hc: shapeless.poly.Case1[HF, V],
                                                    mapValuesTail: MapValuesNull[HF, T]
                                                   ): Aux[HF, FieldType[K, V] :: T, FieldType[K, hc.Result] :: mapValuesTail.Out] =
    new MapValuesNull[HF, FieldType[K, V] :: T] {
      type Out = FieldType[K, hc.Result] :: mapValuesTail.Out
      def apply() = field[K](hc(null.asInstanceOf[V])) :: mapValuesTail.apply
    }
}
