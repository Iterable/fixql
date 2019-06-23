package com.iterable.graphql

import cats.Id
import com.iterable.graphql.compiler.{Compiler, QueryMappings, QueryReducer, ReducerHelpers}
import graphql.Scalars._
import graphql.schema.GraphQLList.list
import graphql.schema.GraphQLNonNull.nonNull
import graphql.schema.idl.SchemaPrinter
import graphql.schema.{GraphQLObjectType, GraphQLSchema, GraphQLType}
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import slick.dbio.DBIO
import slick.jdbc.JdbcBackend

import scala.concurrent.ExecutionContext.Implicits.global

class BuilderDslSpec extends FlatSpec with SchemaAndMappingsMutableBuilderDsl[Id] with SchemaDsl with ReducerHelpers with Matchers {

  private val repo = new CharacterRepo
  private val slickDb = JdbcBackend.Database.forURL("jdbc:h2:mem:test", driver = "org.h2.Driver")

  def buildSchemaAndMappings: (GraphQLSchema, QueryMappings[Id]) = {
    schemaAndMappings { implicit builders =>
      // We have a circular reference between Droid and Human so we need to use type references
      val droidTypeRef = typeRef("Droid")
      val humanTypeRef = typeRef("Human")
      humanMappings(droidTypeRef).include
      droidMappings(humanTypeRef).include

      addMappings(standardMappings)
    }
  }

  def humanMappings(droidType: GraphQLType) = WithBuilders[Id, GraphQLObjectType] { implicit builder =>
    withQueryType { implicit obj =>
      field("humans", list(humanType)) ~> QueryReducer.topLevelObjectsListWithSubfields[Id] {
        repo.getHumans(1000, 0).map(Json.toJson(_).as[JsObject])
      }
    }

    // the lazy val is for the forward reference immediately above
    lazy val humanType = objectType("Human") { implicit obj =>
      field("id", nonNull(GraphQLID)) ~> QueryReducer.mapped(_("id"))
      field("name", nonNull(GraphQLString)) ~> QueryReducer.mapped(_("name"))
      field("friends", list(droidType)) ~> QueryReducer.mapped {_ =>
        JsArray(repo.getDroids(1000, 0).map(Json.toJson(_)))
      }
      field("homePlanet", GraphQLString) ~> QueryReducer.mapped(_("homePlanet"))
    }
    humanType
  }

  def droidMappings(humanType: GraphQLType) = WithBuilders[Id, GraphQLObjectType] { implicit builder =>
    withQueryType { implicit obj =>
      field("droids", list(droidType)) ~> QueryReducer.topLevelObjectsListWithSubfields[Id] {
        repo.getDroids(1000, 0).map(Json.toJson(_).as[JsObject])
      }
    }

    lazy val droidType = objectType("Droid") { implicit obj =>
      field("id", nonNull(GraphQLID)) ~> QueryReducer.mapped(_("id"))
      field("name", nonNull(GraphQLString)) ~> QueryReducer.mapped(_("name"))
      field("friends", list(humanType)) ~> QueryReducer.mapped { _ =>
        JsArray(repo.getHumans(1000, 0).map(Json.toJson(_)))
      }
      field("primaryFunction", GraphQLString) ~> QueryReducer.mapped(_("primaryFunction"))
    }
    droidType
  }


  "builder dsl" should "build a schema that is the same as one written in SDL" in {
    val (schema, mappings) = buildSchemaAndMappings
    val knownSchema = FromGraphQLJava.parseSchema(simplifiedStarWarsSchema)

    val printer = new SchemaPrinter()
    //println(printer.print(schema))
    //println(printer.print(knownSchema))
    printer.print(schema) shouldEqual printer.print(knownSchema)
  }

  "builder dsl" should "build mappings that execute correctly" in {
    val (schema, mappings) = buildSchemaAndMappings

    import qq.droste.syntax.fix._
    val query: Query[Field.Fixed] =
      Query(
        Seq(
          Field("humans",
            subfields = Seq(
              Field("name").fix
            )
          ).fix
        )
      )

    val queryResults = Compiler.compile(FromGraphQLJava.toSchemaFunction(schema), query, mappings)
    val arr = (queryResults \ "humans").as[JsArray]
    arr.value.size shouldEqual repo.getHumans(1000, 0).size
    arr.value.head shouldEqual Json.obj(
      "id" -> "1000",
      "name" -> "Luke Skywalker",
      "friends" -> Seq("1002", "1003", "2000", "2001"),
      "appearsIn" -> Seq("NEWHOPE", "EMPIRE", "JEDI"),
      "homePlanet" -> "Tatooine"
    )
  }

  private val simplifiedStarWarsSchema =
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
