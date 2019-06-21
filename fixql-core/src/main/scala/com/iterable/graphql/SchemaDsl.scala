package com.iterable.graphql

import graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import graphql.schema.{GraphQLList, GraphQLObjectType, GraphQLOutputType, GraphQLType, GraphQLTypeReference}

/**
  * Simple syntactic wrappers around the GraphQL-Java schema builders
  */
trait SchemaDsl {
  protected final def field(name: String, typ: GraphQLOutputType) = {
    newFieldDefinition().name(name).`type`(typ).build()
  }

  def objectType(name: String) = {
    GraphQLObjectType.newObject().name(name)
  }

  def typeRef(typeName: String) = {
    GraphQLTypeReference.typeRef(typeName)
  }

  def list(wrappedType: GraphQLType) = {
    GraphQLList.list(wrappedType)
  }
}
