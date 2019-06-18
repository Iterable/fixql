package com.iterable.graphql

import com.iterable.graphql.compiler.{QueryMappings, QueryReducer}
import graphql.Scalars._
import graphql.schema.GraphQLList.list
import graphql.schema.idl.SchemaPrinter
import graphql.schema.{GraphQLObjectType, GraphQLSchema, GraphQLTypeReference}
import org.scalatest.{FlatSpec, Matchers}

class BuilderSpec extends FlatSpec with SchemaAndMappingsMutableBuilderDsl with SchemaDsl with Matchers {

  def schemaAndMappings2: (GraphQLSchema, QueryMappings) = {
    schemaAndMappings { implicit schema => implicit mappings =>
      queryType("QueryType") { implicit obj =>
        lazy val humanType: GraphQLObjectType = humanMappings(droidType).include
        lazy val droidType: GraphQLObjectType = droidMappings(humanType).include
        droidType
      }
    }
  }

  def humanMappings(droidType: GraphQLObjectType) = WithinQueryType { implicit builder => implicit mappings =>
    field("humans", list(humanType)) ~> QueryReducer.jsObjects { null }

    lazy val humanType = objectType("Human") { implicit obj =>
      field("id", GraphQLID) ~> null
      field("name", GraphQLString) ~> null
      field("friends", list(droidType)) ~> null
      field("homePlanet", GraphQLString) ~> null
    }
    humanType
  }

  def droidMappings(humanType: GraphQLObjectType) = WithinQueryType { implicit builder => implicit mappings =>
    field("droids", list(droidType)) ~> QueryReducer.jsObjects { null }

    lazy val droidType = objectType("Droid") { implicit obj =>
      field("id", GraphQLID) ~> null
      field("name", GraphQLString) ~> null
      field("friends", list(humanType)) ~> null
      field("primaryFunction", GraphQLString) ~> null
    }
    droidType
  }


  "builder" should "build" in {
    val (schema, mappings) = schemaAndMappings2
    val knownSchema = FromGraphQLJava.parseSchema(starWarsSchema)
    //schema shouldEqual knownSchema

    val printer = new SchemaPrinter(SchemaPrinter.Options.defaultOptions())
    println(printer.print(schema))
    println(printer.print(knownSchema))
    printer.print(schema) shouldEqual printer.print(knownSchema)
  }

  val starWarsSchema =
    """
      |    schema {
      |        query: QueryType
      |    }
      |
      |    type QueryType {
      |        humans: [Human]
      |        droids: [Droid]
      |    }
      |
      |    type Human {
      |        id: ID!
      |        name: String!
      |        friends: [Droid]
      |        homePlanet: String
      |    }
      |
      |    type Droid {
      |        id: ID!
      |        name: String!
      |        friends: [Human]
      |        primaryFunction: String
      |    }
    """.stripMargin
}
