package com.iterable.graphql

import com.iterable.graphql.compiler.FieldTypeInfo.{ObjectField, TopLevelField}
import com.iterable.graphql.compiler.{QueryMappings, QueryReducer}
import graphql.schema.{GraphQLFieldDefinition, GraphQLObjectType, GraphQLSchema}
import play.api.libs.json.JsValue

/**
  * Mutable builder for a QueryMappings partial function
  */
class MutableMappingsBuilder[F[_]] {
  private var mappings: QueryMappings[F] = { case _ if false => null }

  def add(m: QueryMappings[F]): Unit = {
    mappings = mappings orElse m
  }

  def build: QueryMappings[F] = mappings
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
case class WithBuilders[F[_], T](mutate: Builders[F] => T) {

  /** Includes our field and mapping definitions in the current context. Example:
    *
    * schemaAndMappings { implicit schema => implicit mappings =>
    *   queryType("Query") { implicit obj =>
    *     myObjectMappings.include
    *   }
    * }
    */
  def include(implicit builder: Builders[F]) = {
    mutate(builder)
  }
}

/**
  * Top-level builders - doesn't include object-type builders other than the builder for the
  * query type
  */
case class Builders[F[_]](
  schemaBuilder: GraphQLSchema.Builder = GraphQLSchema.newSchema(),
  mappingsBuilder: MutableMappingsBuilder[F] = new MutableMappingsBuilder[F],
  queryTypeBuilder: GraphQLObjectType.Builder,
) {
  lazy val queryTypeName = queryTypeBuilder.build.getName
}

/**
  * A mutable builder DSL to define schema and mappings simultaneously.
  * See [[BuilderSpec]] for example usage.
  */
trait SchemaAndMappingsMutableBuilderDsl extends SchemaDsl {

  protected final def schemaAndMappings[F[_]](mutate: Builders[F] => Unit) = {
    val builders = Builders[F](queryTypeBuilder = objectType("QueryType"))
    mutate(builders)
    builders.schemaBuilder.query(builders.queryTypeBuilder.build)
    (builders.schemaBuilder.build, builders.mappingsBuilder.build)
  }

  implicit def mappingsFromBuilders[F[_]](implicit builder: Builders[F]): MutableMappingsBuilder[F] = builder.mappingsBuilder

  /** The Query type uses an ordinary object builder. But since we can't have multiple implicit object builders in
    * lexical scope, uses of the query type builder must be delimited.
    */
  protected final def withQueryType[F[_]](mutate: GraphQLObjectType.Builder => Unit)(implicit builder: Builders[F]) = {
    mutate(builder.queryTypeBuilder)
  }

  protected final def addMappings[F[_]](mappings: QueryMappings[F])(implicit mappingsBuilder: MutableMappingsBuilder[F]): Unit = {
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
    def ~>[F[_]](reducer: QueryReducer[F, JsValue])
          (implicit builders: Builders[F], obj: GraphQLObjectType.Builder, mappings: MutableMappingsBuilder[F]) = {
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
