package com.iterable.graphql.derivation

import graphql.schema.{GraphQLFieldDefinition, GraphQLObjectType, GraphQLOutputType}
import graphql.Scalars._
import shapeless.labelled.{FieldType, field}
import shapeless.ops.record.ToMap
import shapeless.{::, HList, HNil, LabelledGeneric, Poly1}

/**
  * Type class that gives the GraphQLType for a Scala type T
  */
trait IsGraphQLOutputType[T] {
  def graphQLType: GraphQLOutputType
}

private case class PrimitiveIsGraphQLOutputType[T](graphQLType: GraphQLOutputType)
  extends IsGraphQLOutputType[T]

object IsGraphQLOutputType {
  implicit val intIsGraphQLType: IsGraphQLOutputType[Int] = PrimitiveIsGraphQLOutputType(GraphQLInt)
  implicit val stringIsGraphQLType: IsGraphQLOutputType[String] = PrimitiveIsGraphQLOutputType(GraphQLString)
  implicit val boolIsGraphQLType: IsGraphQLOutputType[Boolean] = PrimitiveIsGraphQLOutputType(GraphQLBoolean)
  implicit val longIsGraphQLType: IsGraphQLOutputType[Long] = PrimitiveIsGraphQLOutputType(GraphQLLong)
}

object ToGraphQLType extends Poly1 {
  implicit def fromIsGraphQLType[T](implicit t: IsGraphQLOutputType[T]): Case.Aux[T, GraphQLOutputType] = {
    at[T] { _ => t.graphQLType }
  }

  def derive[T](name: String) = new Derive[T](name)

  class Derive[T](name: String) {
    def toGraphQLObjectType[L <: HList, O <: HList, MV <: HList]
    (implicit
     gen: LabelledGeneric.Aux[T, L],
     mapValues: MapValuesNull.Aux[ToGraphQLType.type, L, MV],
     toMap: ToMap.Aux[MV, Symbol, GraphQLOutputType]
    ) = {
      deriveGraphQLObjectType(name)
    }
  }

  def deriveGraphQLObjectType[T, L <: HList, O <: HList, MV <: HList]
  (typeName: String)
  (implicit
   gen: LabelledGeneric.Aux[T, L],
   mapValues: MapValuesNull.Aux[ToGraphQLType.type, L, MV],
   toMap: ToMap.Aux[MV, Symbol, GraphQLOutputType]
  ): GraphQLObjectType = {
    import scala.collection.JavaConverters.seqAsJavaListConverter
    val mv = mapValues.apply()
    val seq = toMap.apply(mv)
    val fieldDefs = seq.map { case (name, typ) =>
      GraphQLFieldDefinition.newFieldDefinition()
        .name(name.name)
        .`type`(typ)
        .build
    }
    GraphQLObjectType.newObject()
      .name(typeName)
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
