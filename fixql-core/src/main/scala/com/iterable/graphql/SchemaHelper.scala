package com.iterable.graphql

import graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import graphql.schema.{GraphQLObjectType, GraphQLOutputType}

trait SchemaHelper {
  protected final def field(name: String, typ: GraphQLOutputType) = {
    newFieldDefinition().name(name).`type`(typ).build()
  }

  def objectType(name: String) = {
    GraphQLObjectType.newObject().name(name)
  }
}
