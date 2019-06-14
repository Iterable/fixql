package com.iterable.graphql

import com.iterable.graphql.compiler.FieldTypeInfo.ObjectField
import com.iterable.graphql.compiler.{QueryMappings, QueryReducer}
import graphql.schema.{GraphQLFieldDefinition, GraphQLObjectType, GraphQLSchema}
import play.api.libs.json.JsValue

class MutableMappingsBuilder {
  private var mappings: QueryMappings = { case _ if false => null }

  def add(m: QueryMappings): Unit = {
    mappings = mappings orElse m
  }

  def build: QueryMappings = mappings
}

case class WithinQueryType[T](f: GraphQLObjectType.Builder => MutableMappingsBuilder => T) {
  def include(implicit builder: GraphQLObjectType.Builder, mappings: MutableMappingsBuilder) = {
    f(builder)(mappings)
  }
}

trait MutableBuilderDsl {

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

  protected final def queryType(objectType: GraphQLObjectType)(implicit schemaBuilder: GraphQLSchema.Builder) = {
    schemaBuilder.query(objectType)
  }

  implicit class SchemaExtensions(schemaBuilder: GraphQLSchema.Builder) {
    def apply(f: GraphQLSchema.Builder => Unit) = {
      f(schemaBuilder)
      schemaBuilder.build
    }
  }

  implicit class ObjectExtensions(objectBuilder: GraphQLObjectType.Builder) {
    /**
      * Consider making this require implicit MappingsBuilder,
      * f: ObjectTypeBuilder => MappingsBuilder => Unit
      * and passing down the mappings builder.
      *
      * This accomplishes two things (1) this only works in the context of a mappings builder
      * and (2) f accepts an explicit mappings builder... and (3) unifies the type of Build.apply and this method
      * it makes the dependency explicit
      *
      * it allows this to operate in contexts outside of Build { ... }
      * it doesn't imply that a MappingsBuilder might be newly instantiated here
      *
      * @param f
      * @return
      */
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
