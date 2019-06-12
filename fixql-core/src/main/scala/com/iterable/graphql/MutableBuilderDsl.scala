package com.iterable.graphql

import com.iterable.graphql.compiler.FieldTypeInfo.ObjectField
import com.iterable.graphql.compiler.{QueryMappings, QueryReducer}
import graphql.schema.{GraphQLFieldDefinition, GraphQLObjectType}
import play.api.libs.json.JsValue

class MutableMappingsBuilder {
  private var mappings: QueryMappings = { case _ if false => QueryReducer.null }

  def add(m: QueryMappings): Unit = {
    mappings = mappings orElse m
  }
}

trait MutableBuilderDsl {

  /*
  def withinObject[T](f: GraphQLObjectType.Builder => MutableMappingsBuilder => T): GraphQLObjectType.Builder => MutableMappingsBuilder => T = {
    builder => mappings =>
      f(builder)(mappings)
  }

  def withObjectType(name: String)
                    (f: GraphQLObjectType.Builder => Unit): GraphQLObjectType = {
    val builder = GraphQLObjectType.newObject().name(name)
    f(builder)
    builder.build()
  } */

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
      mappings.add({ case ObjectField(ObjectName, field.getName) => reducer })
    }
  }
}
