package com.iterable.graphql

import com.iterable.graphql.compiler.{QueryMappings, QueryReducer}
import graphql.Scalars._
import graphql.schema.GraphQLList.list
import graphql.schema.idl.SchemaPrinter
import graphql.schema.{GraphQLSchema, GraphQLType, GraphQLTypeReference}
import org.scalatest.{FlatSpec, Matchers}

class BuilderSpec extends FlatSpec with SchemaAndMappingsMutableBuilderDsl with SchemaDsl with Matchers {

  def buildSchemaAndMappings: (GraphQLSchema, QueryMappings) = {
    schemaAndMappings { implicit builders =>
        val droidType = GraphQLTypeReference.typeRef("Droid")
        val humanType = GraphQLTypeReference.typeRef("Human")
        humanMappings(droidType).include
        droidMappings(humanType).include
    }
  }

  def humanMappings(droidType: GraphQLType) = WithBuilders { implicit builder =>
    withQueryType { implicit obj =>
      field("humans", list(humanType)) ~> QueryReducer.jsObjects { null }
    }

    lazy val humanType = objectType("Human") { implicit obj =>
      field("id", GraphQLID) ~> null
      field("name", GraphQLString) ~> null
      field("friends", list(droidType)) ~> null
      field("homePlanet", GraphQLString) ~> null
    }
    humanType
  }

  def droidMappings(humanType: GraphQLType) = WithBuilders { implicit builder =>
    withQueryType { implicit obj =>
      field("droids", list(droidType)) ~> QueryReducer.jsObjects { null }
    }

    lazy val droidType = objectType("Droid") { implicit obj =>
      field("id", GraphQLID) ~> null
      field("name", GraphQLString) ~> null
      field("friends", list(humanType)) ~> null
      field("primaryFunction", GraphQLString) ~> null
    }
    droidType
  }


  "builder" should "build" in {
    val (schema, mappings) = buildSchemaAndMappings
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
