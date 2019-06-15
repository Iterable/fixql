package com.iterable.graphql

import com.iterable.graphql.compiler.{QueryMappings, QueryReducer}
import graphql.Scalars
import graphql.schema.GraphQLList.list
import graphql.schema.{GraphQLObjectType, GraphQLSchema}
import org.scalatest.FlatSpec

class BuilderSpec extends FlatSpec with SchemaAndMappingsMutableBuilderDsl with SchemaDsl {

  class Root {
    def schemaAndMappings2: (GraphQLSchema, QueryMappings) = {
      schemaAndMappings { implicit schema => implicit mappings =>
        queryType("Query") { implicit obj =>
          field("humans", list(humanType)) ~> null

          lazy val humanType =
            objectType("Human") { implicit obj =>
              field("name", Scalars.GraphQLString) ~> null
            }
        }
      }
    }
  }

  "builder" should "build" in {
    WithinQueryType { implicit builder => implicit mappings =>
      field("humans", Scalars.GraphQLString) ~> QueryReducer.jsObjects { null }


    }
  }
}
