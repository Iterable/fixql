package com.iterable.graphql

import com.iterable.graphql.compiler.{Compiler, QueryMappings, QueryReducer, ReducerHelpers}
import graphql.Scalars._
import graphql.schema.GraphQLList.list
import graphql.schema.GraphQLNonNull.nonNull
import graphql.schema.idl.SchemaPrinter
import graphql.schema.{GraphQLSchema, GraphQLType}
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.json.{JsObject, Json}
import slick.dbio.DBIO
import slick.jdbc.JdbcBackend

import scala.concurrent.ExecutionContext.Implicits.global

class BuilderDslSpec extends FlatSpec with SchemaAndMappingsMutableBuilderDsl with SchemaDsl with ReducerHelpers with Matchers {

  private val repo = new CharacterRepo
  private val slickDb = JdbcBackend.Database.forURL("jdbc:h2:mem:test", driver = "org.h2.Driver")

  def buildSchemaAndMappings: (GraphQLSchema, QueryMappings) = {
    schemaAndMappings { implicit builders =>
      // We have a circular reference between Droid and Human so we need to use type references
      val droidTypeRef = typeRef("Droid")
      val humanTypeRef = typeRef("Human")
      humanMappings(droidTypeRef).include
      droidMappings(humanTypeRef).include

      addMappings(standardMappings)
    }
  }

  def humanMappings(droidType: GraphQLType) = WithBuilders { implicit builder =>
    withQueryType { implicit obj =>
      field("humans", list(humanType)) ~> QueryReducer.jsObjects { _ =>
        DBIO.successful(repo.getHumans(1000, 0).map(Json.toJson(_).as[JsObject]))
      }
    }

    // the lazy val is for the forward reference immediately above
    lazy val humanType = objectType("Human") { implicit obj =>
      field("id", nonNull(GraphQLID)) ~> QueryReducer.jsValues { parents =>
        DBIO.successful(parents.map(_.apply("id")))
      }
      field("name", nonNull(GraphQLString)) ~> QueryReducer.jsValues { parents =>
        DBIO.successful(parents.map(_.apply("name")))
      }
      field("friends", list(droidType)) ~> QueryReducer.jsObjects { parents =>
        DBIO.successful(repo.getDroids(1000, 0).map(Json.toJson(_).as[JsObject]))
      }
      field("homePlanet", GraphQLString) ~> QueryReducer.jsValues { parents =>
        DBIO.successful(parents.map(_.apply("homePlanet")))
      }
    }
    humanType
  }

  def droidMappings(humanType: GraphQLType) = WithBuilders { implicit builder =>
    withQueryType { implicit obj =>
      field("droids", list(droidType)) ~> QueryReducer.jsObjects { _ =>
        DBIO.successful(repo.getDroids(1000, 0).map(Json.toJson(_).as[JsObject]))
      }
    }

    lazy val droidType = objectType("Droid") { implicit obj =>
      field("id", nonNull(GraphQLID)) ~> QueryReducer.jsValues { parents =>
        DBIO.successful(parents.map(_.apply("id")))
      }
      field("name", nonNull(GraphQLString)) ~> QueryReducer.jsValues { parents =>
        DBIO.successful(parents.map(_.apply("name")))
      }
      field("friends", list(humanType)) ~> QueryReducer.jsObjects { parents =>
        DBIO.successful(repo.getDroids(1000, 0).map(Json.toJson(_).as[JsObject]))
      }
      field("primaryFunction", GraphQLString) ~> QueryReducer.jsValues { parents =>
        DBIO.successful(parents.map(_.apply("primaryFunction")))
      }
    }
    droidType
  }


  "builder dsl" should "build a schema that is the same as one written in SDL" in {
    val (schema, mappings) = buildSchemaAndMappings
    val knownSchema = FromGraphQLJava.parseSchema(simplifiedStarWarsSchema)

    val printer = new SchemaPrinter(SchemaPrinter.Options.defaultOptions())
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

    val dbio = Compiler.compile(FromGraphQLJava.toSchemaFunction(schema), query, mappings)
    slickDb.run(dbio).map { queryResults =>
      queryResults shouldEqual Json.obj(
        "humans" -> Json.obj(
          "id" -> "1000",
          "name" -> "Luke Skywalker",
          "homePlanet" -> "Tatooine"
        )
      )
    }
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
