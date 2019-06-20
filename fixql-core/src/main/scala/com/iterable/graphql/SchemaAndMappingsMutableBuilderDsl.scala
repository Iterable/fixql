package com.iterable.graphql

import com.iterable.graphql.compiler.FieldTypeInfo.{ObjectField, TopLevelField}
import com.iterable.graphql.compiler.{QueryMappings, QueryReducer}
import graphql.schema.{GraphQLFieldDefinition, GraphQLObjectType, GraphQLSchema}
import play.api.libs.json.JsValue

/**
  * Mutable builder for a QueryMappings partial function
  */
class MutableMappingsBuilder {
  private var mappings: QueryMappings = { case _ if false => null }

  def add(m: QueryMappings): Unit = {
    mappings = mappings orElse m
  }

  def build: QueryMappings = mappings
}

/** Supports breaking up schema definition into multiple def's. For example, this lets you do:
  *
  * def myObjectMappings = WithBuilders { implicit builders =>
  *   withQueryType { implicit obj => field("top_level_field") ... }
  *
  *   objectType("myObject") { implicit obj =>
  *     field("my_object_field")..
  *   }
  * }
  *
  * (Note that equivalently you could also do:
  *
  * def myObjectMappings(implicit builders: Builders) = {
  *   ...
  * })
  *
  * @tparam T useful to return a value (e.g. an object type) from this definition block
  */
case class WithBuilders[T](mutate: Builders => T) {

  /** Includes our field and mapping definitions in the current context. Example:
    *
    * schemaAndMappings { implicit schema => implicit mappings =>
    *   queryType("Query") { implicit obj =>
    *     myObjectMappings.include
    *   }
    * }
    */
  def include(implicit builder: Builders) = {
    mutate(builder)
  }
}

/**
  * Top-level builders - doesn't include object-type builders other than the builder for the
  * query type
  */
case class Builders(
  schemaBuilder: GraphQLSchema.Builder = GraphQLSchema.newSchema(),
  mappingsBuilder: MutableMappingsBuilder = new MutableMappingsBuilder,
  queryTypeBuilder: GraphQLObjectType.Builder,
) {
  lazy val queryTypeName = queryTypeBuilder.build.getName
}

/**
  * A mutable builder DSL to define schema and mappings simultaneously.
  * See [[BuilderSpec]] for example usage.
  */
trait SchemaAndMappingsMutableBuilderDsl extends SchemaDsl {

  protected final def schemaAndMappings(mutate: Builders => Unit) = {
    val builders = Builders(queryTypeBuilder = objectType("QueryType"))
    mutate(builders)
    builders.schemaBuilder.query(builders.queryTypeBuilder.build)
    (builders.schemaBuilder.build, builders.mappingsBuilder.build)
  }

  implicit def mappingsFromBuilders(implicit builder: Builders): MutableMappingsBuilder = builder.mappingsBuilder

  /** The Query type uses an ordinary object builder. But since we can't have multiple implicit object builders in
    * lexical scope, uses of the query type builder must be delimited.
    */
  protected final def withQueryType(mutate: GraphQLObjectType.Builder => Unit)(implicit builder: Builders) = {
    mutate(builder.queryTypeBuilder)
  }

  protected final def addMappings(mappings: QueryMappings)(implicit mappingsBuilder: MutableMappingsBuilder): Unit = {
    mappingsBuilder.add(mappings)
  }

  implicit class SchemaExtensions(schemaBuilder: GraphQLSchema.Builder) {
    def apply(f: GraphQLSchema.Builder => Unit) = {
      f(schemaBuilder)
      schemaBuilder.build
    }
  }

  implicit class ObjectExtensions(objectBuilder: GraphQLObjectType.Builder) {
    def apply(f: GraphQLObjectType.Builder => Unit): GraphQLObjectType = {
      f(objectBuilder)
      objectBuilder.build()
    }
  }

  implicit class FieldExtensions(field: GraphQLFieldDefinition) {
    def ~>(reducer: QueryReducer[JsValue])
          (implicit builders: Builders, obj: GraphQLObjectType.Builder, mappings: MutableMappingsBuilder) = {
      obj.field(field)
      val ObjectName = obj.build.getName
      val FieldName = field.getName
      if (ObjectName == builders.queryTypeName) {
        mappings.add({ case TopLevelField(FieldName) => reducer })
      } else {
        mappings.add({ case ObjectField(ObjectName, FieldName) => reducer })
      }
    }
  }
}
