package com.iterable.graphql.introspection

import cats.Monad
import com.iterable.graphql.compiler.FieldTypeInfo.{ObjectField, TopLevelField}
import com.iterable.graphql.compiler.{QueryMappings, QueryReducer}
import graphql.introspection.Introspection
import graphql.schema.{GraphQLEnumType, GraphQLFieldDefinition, GraphQLInterfaceType, GraphQLObjectType, GraphQLScalarType, GraphQLSchema, GraphQLType, GraphQLTypeUtil}
import play.api.libs.json.{JsArray, JsNull, JsObject, JsValue, Json}

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.JavaConverters.setAsJavaSetConverter

class IntrospectionMappings(graphqlSchema: GraphQLSchema) {

  def newSchemaAndMappings[F[_] : Monad](existingMappings: QueryMappings[F]): (GraphQLSchema, QueryMappings[F]) = {
    val newSchema = GraphQLSchema.newSchema(graphqlSchema)
      .additionalTypes(Set[GraphQLType](Introspection.__Schema).asJava)
      .build
    (newSchema, existingMappings orElse introspectionMappings(newSchema))
  }

  def introspectionMappings[F[_]](schema2: GraphQLSchema)(implicit F: Monad[F]): QueryMappings[F] = {
    case TopLevelField("__schema") => QueryReducer.jsObjects[F] { _ =>
      F.pure(Seq(Json.toJson(schema).as[JsObject]))
    }
        .mergeResolveSubfields
        .as[JsValue]
    case ObjectField("__Schema", "types") => QueryReducer.jsObjects[F] { _ =>
      F.pure(allTypes.map(Json.toJson(_).as[JsObject]))
    }
        .mergeResolveSubfields
        .toTopLevelArray
    case ObjectField("__Schema", "queryType") => QueryReducer.jsObjects[F] { _ =>
      F.pure(Seq(Json.toJson(mkType(graphqlSchema.getQueryType)).as[JsObject]))
    }
        .mergeResolveSubfields
        .toTopLevelArray
    case ObjectField("__Schema", _) => QueryReducer.jsValues[F] { _ => F.pure(Seq(JsNull)) }.toTopLevelArray
    case ObjectField("__Type", "kind") => QueryReducer.mapped(_("kind"))
    case ObjectField("__Type", "name") => QueryReducer.mapped(_("name"))
    case ObjectField("__Type", "description") =>  QueryReducer.mapped(o => (o \ "description").asOpt[JsValue].getOrElse(JsNull))
    case ObjectField("__Type", "fields") => QueryReducer.mapped(_("fields"))
    case ObjectField("__Type", fieldName) => QueryReducer.jsValues[F] { parents =>
      val field = schema2.getObjectType("__Type").getFieldDefinition(fieldName)
      val placeholder =
        if (GraphQLTypeUtil.isList(field.getType)) {
          JsArray()
        } else {
          JsNull
        }
      F.pure(Seq.fill(parents.size)(placeholder))
    }
    case ObjectField("__Field", fieldName) => QueryReducer.mapped(_(fieldName))
    case ObjectField("__InputValue", fieldName) => QueryReducer.mapped(_(fieldName))
    case ObjectField("__EnumValue", fieldName) => QueryReducer.mapped(_(fieldName)) //o => (o \ fieldName).asOpt[JsValue].getOrElse(JsNull))
    case ObjectField("__Directive", fieldName) => QueryReducer.mapped(_(fieldName))
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
      case scalar: GraphQLScalarType =>
        __Type(
          kind = "SCALAR",
          name = Option(scalar.getName),
          description = Option(scalar.getDescription),
        )
      case enum: GraphQLEnumType =>
        __Type(
          kind = "ENUM",
          name = Option(enum.getName),
          description = Option(enum.getDescription),
        )
      case intf: GraphQLInterfaceType =>
        __Type(
          kind = "INTERFACE",
          name = Option(intf.getName),
          description = Option(intf.getDescription),
        )
    }.toSeq
  }

  def mkType(obj: GraphQLObjectType) = {
    __Type(
      kind = "OBJECT",
      name = Option(obj.getName),
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
        typeName = "", // TODO
        isDeprecated = field.isDeprecated,
        deprecationReason = Option(field.getDeprecationReason),
      )
    }
  }
}
