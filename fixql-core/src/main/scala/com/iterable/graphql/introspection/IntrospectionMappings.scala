package com.iterable.graphql.introspection

import cats.Monad
import com.iterable.graphql.compiler.FieldTypeInfo.{ObjectField, TopLevelField}
import com.iterable.graphql.compiler.{QueryMappings, QueryReducer}
import graphql.introspection.Introspection
import graphql.schema.{GraphQLFieldDefinition, GraphQLObjectType, GraphQLSchema, GraphQLType}
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.JavaConverters.setAsJavaSetConverter

class IntrospectionMappings(graphqlSchema: GraphQLSchema) {

  def newSchemaAndMappings[F[_] : Monad](existingMappings: QueryMappings[F]) = {
    (GraphQLSchema.newSchema(graphqlSchema)
      .additionalTypes(Set[GraphQLType](Introspection.__Schema).asJava)
      .build, existingMappings orElse introspectionMappings)
  }

  def introspectionMappings[F[_]](implicit F: Monad[F]): QueryMappings[F] = {
    case TopLevelField("__schema") => QueryReducer.jsObjects[F] { _ =>
      F.pure(Seq(Json.toJson(schema).as[JsObject]))
    }
        .mergeResolveSubfields
        .as[JsValue]
    case ObjectField("__Schema", "types") => QueryReducer.jsObjects[F] { _ =>
      F.pure(allTypes
        .map(Json.toJson(_).as[JsObject]))
    }
      .mergeResolveSubfields
      .toTopLevelArray
    case ObjectField("__Type", "kind") => QueryReducer.mapped(_("kind"))
    case ObjectField("__Type", "name") => QueryReducer.mapped(_("name"))
    case ObjectField("__Type", "description") =>  QueryReducer.mapped(_("description"))
    case ObjectField("__Type", "fields") => QueryReducer.mapped(_("fields"))
  }

  def schema = {
    val queryType = mkType(graphqlSchema.getQueryType)
    __Schema(
      allTypes,
      queryType,
    )
  }

  def allTypes: Seq[__Type] = {
    graphqlSchema.getAllTypesAsList.asScala.map {
      case obj: GraphQLObjectType => mkType(obj)
    }.toSeq
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
