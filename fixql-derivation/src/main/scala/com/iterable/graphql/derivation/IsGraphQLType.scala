package com.iterable.graphql.derivation

import graphql.schema.GraphQLType
import graphql.Scalars._

trait IsGraphQLType[T] {
  def graphQLType: GraphQLType
}

private case class PrimitiveIsGraphQLType[T](val graphQLType: GraphQLType)
  extends IsGraphQLType[T]

object IsGraphQLType {
  implicit val stringIsGraphQLType: IsGraphQLType[String] = PrimitiveIsGraphQLType[String](GraphQLString)
  implicit val intIsGraphQLType: IsGraphQLType[Int] = PrimitiveIsGraphQLType[Int](GraphQLInt)
  implicit val boolIsGraphQLType: IsGraphQLType[Boolean] = PrimitiveIsGraphQLType[Boolean](GraphQLBoolean)
}