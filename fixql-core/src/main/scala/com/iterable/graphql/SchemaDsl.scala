package com.iterable.graphql

import graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import graphql.schema.{GraphQLObjectType, GraphQLOutputType, GraphQLTypeReference}

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
}
