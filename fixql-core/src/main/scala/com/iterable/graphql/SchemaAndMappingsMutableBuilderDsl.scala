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

/** Supports "modular" definition of schemas using builders. This lets you do:
  *
  * def myObjectMappings = WithinQueryType { implicit obj => implicit mappings =>
  *   field("top_level_field") ...
  *
  *   objectType("myObject") { implicit obj =>
  *     field("my_object_field")..
  *   }
  * }
  *
  * Note that equivalently you could also do:
  *
  * def myObjectMappings(implicit obj: GraphQLObject.Builder, mappings: MutableMappingsBuilder) = {
  *   ...
  * }
  *
  * @tparam T useful to return a value (e.g. an object type) from this module
  */
case class WithinQueryType[T](mutate: GraphQLObjectType.Builder => MutableMappingsBuilder => T) {

  /** Includes our field and mapping definitions in the current context. Example:
    *
    * schemaAndMappings { implicit schema => implicit mappings =>
    *   queryType("Query") { implicit obj =>
    *     myObjectMappings.include
    *   }
    * }
    */
  def include(implicit builder: GraphQLObjectType.Builder, mappings: MutableMappingsBuilder) = {
    mutate(builder)(mappings)
  }
}

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
  protected final def schemaAndMappings(f: GraphQLSchema.Builder => MutableMappingsBuilder => Unit) = {
    val schema = GraphQLSchema.newSchema()
    val mappings = new MutableMappingsBuilder
    f(schema)(mappings)
    (schema.build, mappings.build)
  }

  protected final def queryType(name: String)(f: GraphQLObjectType.Builder => Unit)(implicit schemaBuilder: GraphQLSchema.Builder) = {
    val obj = objectType(name)(f)
    schemaBuilder.query(obj)
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
