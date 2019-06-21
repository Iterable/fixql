package com.iterable.graphql.derivation

import graphql.schema.{GraphQLFieldDefinition, GraphQLObjectType, GraphQLOutputType, GraphQLTypeUtil}
import graphql.schema.GraphQLNonNull.nonNull
import graphql.Scalars._
import shapeless.labelled.{FieldType, field}
import shapeless.ops.record.ToMap
import shapeless.{::, <:!<, HList, HNil, LabelledGeneric, Poly1}

/**
  * Type class that gives the GraphQLType for a Scala type T
  */
trait IsGraphQLOutputType[T] {
  def graphQLType: GraphQLOutputType
}

private case class SimpleIsGraphQLOutputType[T](graphQLType: GraphQLOutputType)
  extends IsGraphQLOutputType[T]

object IsGraphQLOutputType {
  implicit val intIsGraphQLType: IsGraphQLOutputType[Int] =
    SimpleIsGraphQLOutputType(nonNull(GraphQLInt))
  implicit val stringIsGraphQLType: IsGraphQLOutputType[String] =
    SimpleIsGraphQLOutputType(nonNull(GraphQLString))
  implicit val boolIsGraphQLType: IsGraphQLOutputType[Boolean] =
    SimpleIsGraphQLOutputType(nonNull(GraphQLBoolean))
  implicit val longIsGraphQLType: IsGraphQLOutputType[Long] =
    SimpleIsGraphQLOutputType(nonNull(GraphQLLong))

  implicit def optIsGraphQLType[T]
  (implicit t: IsGraphQLOutputType[T],
   notOption: T <:!< Option[_]): IsGraphQLOutputType[Option[T]] = {
    SimpleIsGraphQLOutputType(
      GraphQLTypeUtil.unwrapNonNull(t.graphQLType).asInstanceOf[GraphQLOutputType]
    )
  }
}

object ToGraphQLType extends Poly1 {
  /** Note this could be better done with a Poly0 but using a Poly1 lets me
    * use MapValuesNull. What I really want is "FillValuesWith" - a combination of
    * MapValues and FillWith.
    */
  implicit def toGraphQLType[T](implicit t: IsGraphQLOutputType[T]): Case.Aux[T, GraphQLOutputType] = {
    at[T] { _ => t.graphQLType }
  }
}

object DeriveGraphQLType extends Poly1 {
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
