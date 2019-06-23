package com.iterable.graphql.derivation

import cats.Id
import com.iterable.graphql.compiler.{Compiler, QueryReducer, ReducerHelpers}
import com.iterable.graphql.{CharacterRepo, FromGraphQLJava, Human, SchemaAndMappingsMutableBuilderDsl}
import graphql.schema.GraphQLObjectType
import graphql.Scalars._
import graphql.schema.idl.SchemaPrinter
import io.circe.Json
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.json.{JsArray, JsNull, JsObject, JsString, Json => PlayJson}
import slick.jdbc.JdbcBackend

class DerivationSpec extends FlatSpec with Matchers
  with SchemaAndMappingsMutableBuilderDsl with ReducerHelpers with DerivationBuilderDsl {

  case class Test(foo: String, bar: Int, rg: Seq[Long])

  "object type derivation" should "generate the expected object type" in {
    val objectType = DeriveGraphQLType[Test]("Test").allFields
    val expected = GraphQLObjectType.newObject()
      .name("Test")
      .field(field("foo", nonNull(GraphQLString)))
      .field(field("bar", nonNull(GraphQLInt)))
      .field(field("rg", list(nonNull(GraphQLLong))))
      .build

    val printer = new SchemaPrinter()
    printer.print(objectType) shouldEqual printer.print(expected)
  }

  private val repo = new CharacterRepo
  private val slickDb = JdbcBackend.Database.forURL("jdbc:h2:mem:test", driver = "org.h2.Driver")

  // TODO: add negative tests to verify query syntax validation fails when you
  // specify fields that aren't in the case class
  "mappings derivation" should "generate mappings that execute correctly" in {
    val (schema, mappings) =
      schemaAndMappings[Id] { implicit builders =>
        withQueryType { implicit obj =>
          field("humans", list(humanType)) ~> QueryReducer.topLevelObjectsListWithSubfields[Id] {
            repo.getHumans(1000, 0).map(PlayJson.toJson(_).as[JsObject])
          }
        }

        lazy val humanType =
          DeriveGraphQLType[Human]("Human").fields('id, 'name)
        addMappings(DeriveMappings[Human]("Human").fields('id, 'name))

        addMappings(standardMappings)
      }

    val queryStr = "{ humans { id name } }"
    val query = FromGraphQLJava.parseAndValidateQuery(schema, queryStr, Json.obj())
    val queryResults = Compiler.compile[Id](FromGraphQLJava.toSchemaFunction(schema), query.get, mappings)
      val arr = (queryResults \ "humans").as[JsArray]
      arr.value.size shouldEqual repo.getHumans(1000, 0).size
      arr.value.head shouldEqual PlayJson.obj(
        "id" -> "1000",
        "name" -> "Luke Skywalker",
        //"friends" -> Seq("1002", "1003", "2000", "2001"),
        //"appearsIn" -> Seq("NEWHOPE", "EMPIRE", "JEDI"),
        //"homePlanet" -> "Tatooine"
      )
  }

  "derivation builder dsl" should "generate mappings that execute correctly" in {
    val (schema, mappings) =
      schemaAndMappings[Id] { implicit builders =>
        withQueryType { implicit obj =>
          field("humans", list(humanType)) ~> QueryReducer.topLevelObjectsListWithSubfields[Id] {
            repo.getHumans(1000, 0).map(PlayJson.toJson(_).as[JsObject])
          }
        }

        lazy val humanType =
          objectType("Human") { implicit obj =>
            field("id", GraphQLID) ~> QueryReducer.mapped(_("id"))

            addDerived[Human].fieldsAndMappings('name, 'homePlanet)
          }

        addMappings(standardMappings)
      }

    val printer = new SchemaPrinter()
    printer.print(schema) shouldEqual printer.print(knownSchema)

    val queryStr = "{ humans { id name homePlanet } }"
    val query = FromGraphQLJava.parseAndValidateQuery(schema, queryStr, Json.obj())
    val queryResults = Compiler.compile(FromGraphQLJava.toSchemaFunction(schema), query.get, mappings)
      val arr = (queryResults \ "humans").as[JsArray]
      arr.value.size shouldEqual repo.getHumans(1000, 0).size
      arr.value.head shouldEqual PlayJson.obj(
        "id" -> "1000",
        "name" -> "Luke Skywalker",
        "homePlanet" -> "Tatooine",
      )
      arr.value.find(_("name") == JsString("Han Solo")).get.apply("homePlanet") shouldEqual JsNull
  }

  private val knownSchema = FromGraphQLJava.parseSchema(
    """
      |schema {
      |  query: QueryType
      |}
      |
      |type QueryType {
      |  humans: [Human]
      |}
      |
      |type Human {
      |  id: ID
      |  name: String
      |  homePlanet: String
      |}
    """.stripMargin)
}
