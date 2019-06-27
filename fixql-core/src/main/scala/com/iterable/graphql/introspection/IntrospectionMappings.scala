package com.iterable.graphql.introspection

import com.iterable.graphql.compiler.FieldTypeInfo.TopLevelField
import graphql.schema.{GraphQLFieldDefinition, GraphQLObjectType, GraphQLSchema}

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

class IntrospectionMappings(graphqlSchema: GraphQLSchema) {

  def schema = {
    val types =
      graphqlSchema.getAllTypesAsList.asScala.map {
        case obj: GraphQLObjectType => mkType(obj)
      }
    val queryType = mkType(graphqlSchema.getQueryType)
    __Schema(
      types.toSeq,
      queryType,
    )
  }

  def mkType(obj: GraphQLObjectType) = {
    __Type(
      kind = "OBJECT",
      name = obj.getName,
      description = Option(obj.getDescription),
      fields = mkFields(obj.getFieldDefinitions.asScala.toSeq),
    )
  }


  def mkFields(fields: Seq[GraphQLFieldDefinition]) = {
    fields.map { field =>
      __Field(
        name = field.getName,
        description = Option(field.getDescription),
        args = Nil,
        `type` = ???,
        isDeprecated = field.isDeprecated,
        deprecationReason = Option(field.getDeprecationReason),
      )
    }
  }
}
