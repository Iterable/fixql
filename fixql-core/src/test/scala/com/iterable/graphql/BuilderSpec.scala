package com.iterable.graphql

import com.iterable.graphql.compiler.{QueryMappings, QueryReducer}
import graphql.Scalars._
import graphql.schema.GraphQLList.list
import graphql.schema.GraphQLNonNull.nonNull
import graphql.schema.idl.SchemaPrinter
import graphql.schema.{GraphQLSchema, GraphQLType}
import org.scalatest.{FlatSpec, Matchers}

class BuilderSpec extends FlatSpec with SchemaAndMappingsMutableBuilderDsl with SchemaDsl with Matchers {

  def buildSchemaAndMappings: (GraphQLSchema, QueryMappings) = {
    schemaAndMappings { implicit builders =>
      // We have a circular reference between Droid and Human so we need to use type references
      val droidTypeRef = typeRef("Droid")
      val humanTypeRef = typeRef("Human")
      humanMappings(droidTypeRef).include
      droidMappings(humanTypeRef).include
    }
  }

  def humanMappings(droidType: GraphQLType) = WithBuilders { implicit builder =>
    withQueryType { implicit obj =>
      field("humans", list(humanType)) ~> QueryReducer.jsObjects { null }
    }

    // the lazy val is for the forward reference immediately above
    lazy val humanType = objectType("Human") { implicit obj =>
      field("id", nonNull(GraphQLID)) ~> null
      field("name", nonNull(GraphQLString)) ~> null
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
      field("id", nonNull(GraphQLID)) ~> null
      field("name", nonNull(GraphQLString)) ~> null
      field("friends", list(humanType)) ~> null
      field("primaryFunction", GraphQLString) ~> null
    }
    droidType
  }


  "builder dsl" should "yield a schema that is the same as one written in SDL" in {
    val (schema, mappings) = buildSchemaAndMappings
    val knownSchema = FromGraphQLJava.parseSchema(starWarsSchema)

    val printer = new SchemaPrinter(SchemaPrinter.Options.defaultOptions())
    //println(printer.print(schema))
    //println(printer.print(knownSchema))
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
