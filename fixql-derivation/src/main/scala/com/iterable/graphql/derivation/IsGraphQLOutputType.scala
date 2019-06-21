package com.iterable.graphql.derivation

import graphql.schema.{GraphQLOutputType, GraphQLTypeUtil}
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

  implicit def optIsGraphQLType[T]
  (implicit t: IsGraphQLOutputType[T],
   notOption: T <:!< Option[_]): IsGraphQLOutputType[Option[T]] = {
    SimpleIsGraphQLOutputType(
      GraphQLTypeUtil.unwrapNonNull(t.graphQLType).asInstanceOf[GraphQLOutputType]
    )
  }
}

