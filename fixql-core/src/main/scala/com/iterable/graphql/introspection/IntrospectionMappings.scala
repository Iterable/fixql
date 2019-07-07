package com.iterable.graphql.introspection

import cats.Monad
import com.iterable.graphql.compiler.FieldTypeInfo.{ObjectField, TopLevelField}
import com.iterable.graphql.compiler.{QueryMappings, QueryReducer, ResolverFn}
import graphql.introspection.Introspection
import graphql.schema.{GraphQLEnumType, GraphQLFieldDefinition, GraphQLInterfaceType, GraphQLList, GraphQLNonNull, GraphQLObjectType, GraphQLScalarType, GraphQLSchema, GraphQLType, GraphQLTypeUtil}
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
      F.pure(Seq(Json.toJson(mkObjectType(graphqlSchema.getQueryType)).as[JsObject]))
    }
        .mergeResolveSubfields
        .as[JsValue]
    case ObjectField("__Schema", fieldName) => QueryReducer.jsValues[F] { parents =>
      val field = schema2.getObjectType("__Schema").getFieldDefinition(fieldName)
      val placeholder =
        if (GraphQLTypeUtil.isList(field.getType)) {
          JsArray()
        } else {
          JsNull
        }
      F.pure(Seq.fill(parents.size)(placeholder))
    }
    case ObjectField("__Type", "kind") => QueryReducer.mapped(_("kind"))
    case ObjectField("__Type", "name") => QueryReducer.mapped(o => (o \ "name").asOpt[JsValue].getOrElse(JsNull))
    case ObjectField("__Type", "description") =>  QueryReducer.mapped(o => (o \ "description").asOpt[JsValue].getOrElse(JsNull))
    case ObjectField("__Type", "ofType") => QueryReducer.mapped(o => (o \ "ofType").asOpt[JsValue].getOrElse(JsNull))
    case ObjectField("__Type", "fields") => QueryReducer.apply[F, Seq[JsObject]] { field =>
      ResolverFn(field.name) { parents =>
        F.pure(parents.map { parent =>
          parent("name").asOpt[String].flatMap { typeName =>
            schema2.getType(typeName) match {
              case obj: GraphQLObjectType =>
                Some(mkFields(obj.getFieldDefinitions.asScala.toSeq)
                  .map(Json.toJson(_).as[JsObject]))
              case _ => None
            }
          }.getOrElse(Nil)
        })
      }
    }
        .mergeResolveSubfieldsMany
        .as[JsValue]
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
    case ObjectField("__Field", "type") => QueryReducer.jsObjects { parents =>
      F.pure(parents.map { parent =>
        parent("type").as[JsObject]
      })
    }
        .mergeResolveSubfields
        .as[JsValue]
    case ObjectField("__Field", fieldName) => QueryReducer.mapped(p => (p \ fieldName).asOpt[JsValue].getOrElse(JsNull))
    case ObjectField("__InputValue", fieldName) => QueryReducer.mapped(_(fieldName))
    case ObjectField("__EnumValue", fieldName) => QueryReducer.mapped(_(fieldName)) //o => (o \ fieldName).asOpt[JsValue].getOrElse(JsNull))
    case ObjectField("__Directive", fieldName) => QueryReducer.mapped(_(fieldName))
  }

  def schema = {
    val queryType = mkObjectType(graphqlSchema.getQueryType)
    __Schema(
      allTypes,
      queryType,
    )
  }

  def allTypes: Seq[__Type] = {
    graphqlSchema.getAllTypesAsList.asScala.map(mkType).toSeq
  }

  def mkType(typ: GraphQLType): __Type = {
    typ match {
      case obj: GraphQLObjectType => mkObjectType(obj)
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
      case list: GraphQLList =>
        __Type(
          kind = "LIST",
          name = None,
          description = None,
          ofType = Some(mkType(list.getWrappedType))
        )
      case non: GraphQLNonNull =>
        __Type(
          kind = "NON_NULL",
          name = None,
          description = None,
          ofType = Some(mkType(non.getWrappedType))
        )
    }
  }

  def mkObjectType(obj: GraphQLObjectType) = {
    __Type(
      kind = "OBJECT",
      name = Option(obj.getName),
      description = Option(obj.getDescription),
    )
  }

  def mkFields(fields: Seq[GraphQLFieldDefinition]) = {
    fields.map { field =>
      __Field(
        name = field.getName,
        description = Option(field.getDescription),
        args = Nil,
        `type` = mkType(field.getType),
        isDeprecated = field.isDeprecated,
        deprecationReason = Option(field.getDeprecationReason),
      )
    }
  }
}
