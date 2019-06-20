package com.iterable.graphql.derivation

import graphql.schema.{GraphQLFieldDefinition, GraphQLObjectType, GraphQLOutputType, GraphQLType}
import graphql.Scalars._
import shapeless.labelled.{FieldType, field}
import shapeless.ops.record.ToMap
import shapeless.{::, HList, HNil, LabelledGeneric, Poly1}

object ToGraphQLType extends Poly1 {
  implicit val caseInt = at[Int] { _ => GraphQLInt }
  implicit val caseStr = at[String] { _ => GraphQLString }
  implicit val caseBool = at[Boolean] { _ => GraphQLBoolean }
}

object ToGraphQLOutputType {
  class Derive[T](name: String) {
    def toGraphQLObjectType[L <: HList, O <: HList, MV <: HList]
    (implicit
     gen: LabelledGeneric.Aux[T, L],
     mapValues: MapValuesNull.Aux[ToGraphQLType.type, L, MV],
     toMap: ToMap.Aux[MV, Symbol, Any]
    ) = {
      deriveGraphQLObjectType(name)
    }
  }

  def deriveGraphQLObjectType[T, L <: HList, O <: HList, MV <: HList]
  (name: String)
  (implicit
   gen: LabelledGeneric.Aux[T, L],
   mapValues: MapValuesNull.Aux[ToGraphQLType.type, L, MV],
   toMap: ToMap.Aux[MV, Symbol, Any]
  ): GraphQLObjectType = {
    import scala.collection.JavaConverters.seqAsJavaListConverter
    val mv = mapValues.apply()
    val seq = toMap.apply(mv)
    val fieldDefs = seq.map { case (name, typ) =>
        GraphQLFieldDefinition.newFieldDefinition()
        .name(name.name)
        .`type`(typ.asInstanceOf[GraphQLOutputType])
        .build
    }
    GraphQLObjectType.newObject()
      .name(name)
      .fields(fieldDefs.toSeq.asJava)
      .build
  }

  case class Test(foo: String, bar: Int)

  def main(args: Array[String]): Unit = {
    val typ = new Derive[Test]("Test").toGraphQLObjectType
    println(typ)
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
