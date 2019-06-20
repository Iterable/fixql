package com.iterable.graphql

import com.iterable.graphql.compiler.FieldTypeInfo.ObjectField
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
  * def myObjectMappings = WithinQueryType { implicit obj => implicit mappings =>
  *   field("top_level_field") ...
  *
  *   objectType("myObject") { implicit obj =>
  *     field("my_object_field")..
  *   }
  * }
  *
  * (Note that equivalently you could also do:
  *
  * def myObjectMappings(implicit obj: GraphQLObject.Builder, mappings: MutableMappingsBuilder) = {
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
                        )

/**
  * A mutable builder DSL to define schema and mappings simultaneously.
  * See [[BuilderSpec]] for example usage.
  */
trait SchemaAndMappingsMutableBuilderDsl extends SchemaDsl {

  /**
    * do we really want to define schema and mappings simultaneously?
    * what about when one has no dependency on user but the other does?
    * neither should depend on user. instead the dependency on user should be pushed down to Kleisli
    */
  protected final def schemaAndMappings(mutate: Builders => Unit) = {
    val builders = Builders(queryTypeBuilder = objectType("QueryType"))
    mutate(builders)
    builders.schemaBuilder.query(builders.queryTypeBuilder.build)
    (builders.schemaBuilder.build, builders.mappingsBuilder.build)
  }

  implicit def mappingsFromBuilder(implicit builder: Builders): MutableMappingsBuilder = builder.mappingsBuilder

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
          (implicit builder: GraphQLObjectType.Builder, mappings: MutableMappingsBuilder) = {
      builder.field(field)
      val ObjectName = builder.build.getName
      val FieldName = field.getName
      mappings.add({ case ObjectField(ObjectName, FieldName) => reducer })
    }
  }
}
