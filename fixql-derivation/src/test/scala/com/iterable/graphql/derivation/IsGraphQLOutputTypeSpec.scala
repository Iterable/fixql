package com.iterable.graphql.derivation

import com.iterable.graphql.compiler.{Compiler, QueryReducer, ReducerHelpers}
import com.iterable.graphql.{CharacterRepo, FromGraphQLJava, Human, SchemaAndMappingsMutableBuilderDsl}
import graphql.schema.GraphQLObjectType
import graphql.Scalars._
import graphql.schema.idl.SchemaPrinter
import io.circe.Json
import org.scalatest.{AsyncFlatSpec, Matchers}
import play.api.libs.json.{JsArray, JsObject, Json => PlayJson}
import shapeless.HNil
import slick.dbio.DBIO
import slick.jdbc.JdbcBackend

class IsGraphQLOutputTypeSpec extends AsyncFlatSpec with Matchers
  with SchemaAndMappingsMutableBuilderDsl with ReducerHelpers {

  case class Test(foo: String, bar: Int)

  "object type derivation" should "generate the expected object type" in {
    val objectType = DeriveGraphQLType.derive[Test]("Test").allFields

    val expected = GraphQLObjectType.newObject()
      .name("Test")
      .field(field("foo", nonNull(GraphQLString)))
      .field(field("bar", nonNull(GraphQLInt)))
      .build

    val printer = new SchemaPrinter(SchemaPrinter.Options.defaultOptions())
    printer.print(objectType) shouldEqual printer.print(expected)
  }

  private val repo = new CharacterRepo
  private val slickDb = JdbcBackend.Database.forURL("jdbc:h2:mem:test", driver = "org.h2.Driver")

  "mappings derivation" should "generate mappings that execute correctly" in {
    val (schema, mappings) =
      schemaAndMappings { implicit builders =>
        withQueryType { implicit obj =>
          field("humans", list(humanType)) ~> QueryReducer.jsObjects { _ =>
            DBIO.successful(repo.getHumans(1000, 0).map(PlayJson.toJson(_).as[JsObject]))
          }
            .mergeResolveSubfields
            .toTopLevelArray
        }

        import shapeless.syntax.singleton._
        lazy val humanType =
          DeriveGraphQLType.derive[Human]("Human").selected('id.narrow :: 'name.narrow :: HNil)
        addMappings(DeriveMappings.derive[Human]("Human").selected('id.narrow :: 'name.narrow :: HNil))

        addMappings(standardMappings)
      }

    val queryStr = "{ humans { id name } }"
    val query = FromGraphQLJava.parseAndValidateQuery(schema, queryStr, Json.obj())
    val dbio = Compiler.compile(FromGraphQLJava.toSchemaFunction(schema), query.get, mappings)
    slickDb.run(dbio).map { queryResults =>
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
  }
}
