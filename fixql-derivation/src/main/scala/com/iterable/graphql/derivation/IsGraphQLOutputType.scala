package com.iterable.graphql.derivation

import java.math.BigInteger

import graphql.schema.{GraphQLList, GraphQLOutputType, GraphQLTypeUtil}
import graphql.schema.GraphQLNonNull.nonNull
import graphql.Scalars._
import shapeless.<:!<

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
  implicit val doubleIsGraphQLType: IsGraphQLOutputType[Double] =
    SimpleIsGraphQLOutputType(nonNull(GraphQLFloat))
  implicit val charIsGraphQLType: IsGraphQLOutputType[Char] =
    SimpleIsGraphQLOutputType(nonNull(GraphQLChar))
  implicit val byteIsGraphQLType: IsGraphQLOutputType[Byte] =
    SimpleIsGraphQLOutputType(nonNull(GraphQLByte))
  implicit val shortIsGraphQLType: IsGraphQLOutputType[Short] =
    SimpleIsGraphQLOutputType(nonNull(GraphQLShort))
  implicit val bigIntIsGraphQLType: IsGraphQLOutputType[BigInteger] =
    SimpleIsGraphQLOutputType(nonNull(GraphQLBigInteger))
  implicit val bigDecimalIsGraphQLType: IsGraphQLOutputType[BigDecimal] =
    SimpleIsGraphQLOutputType(nonNull(GraphQLBigDecimal))

  /**
    * @param notOption GraphQL has no corresponding type for Option[Option[A]] so we suppress it for derivation
    *                  by disallowing T <: Option{_]
    */
  implicit def optIsGraphQLType[T]
  (implicit t: IsGraphQLOutputType[T],
   notOption: T <:!< Option[_]): IsGraphQLOutputType[Option[T]] = {
    SimpleIsGraphQLOutputType(
      GraphQLTypeUtil.unwrapNonNull(t.graphQLType).asInstanceOf[GraphQLOutputType]
    )
  }

  implicit def seqIsGraphQLType[T](implicit t: IsGraphQLOutputType[T]): IsGraphQLOutputType[Seq[T]] = {
    SimpleIsGraphQLOutputType(
      GraphQLList.list(t.graphQLType)
    )
  }
}

