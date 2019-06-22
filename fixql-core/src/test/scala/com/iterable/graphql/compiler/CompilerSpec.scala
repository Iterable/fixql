package com.iterable.graphql.compiler

import com.iterable.graphql.FromGraphQLJava.parseSchema
import com.iterable.graphql.compiler.FieldTypeInfo.{ObjectField, TopLevelField}
import com.iterable.graphql.{CharacterRepo, Field, FromGraphQLJava, Query, StarWarsSchema}
import org.scalatest.{AsyncFlatSpec, Matchers}
import play.api.libs.json.{JsArray, JsObject, Json}
import slick.jdbc.JdbcBackend
import slick.dbio.DBIO

class CompilerSpec extends AsyncFlatSpec with Matchers with StarWarsSchema with ReducerHelpers {

  private val slickDb = JdbcBackend.Database.forURL("jdbc:h2:mem:test", driver = "org.h2.Driver")

  "Compiler" should "compile" in {
    val graphQLSchema = parseSchema(starWarsSchema)
    val schema = FromGraphQLJava.toSchemaFunction(graphQLSchema)
    val repo = new CharacterRepo

    val resolvers = ({
      case TopLevelField("humans") => QueryReducer.jsObjects { _ =>
        DBIO.successful(repo.getHumans(1000, 0).map(Json.toJson(_).as[JsObject]))
      }
          .toTopLevelArray
      case TopLevelField("droids") => QueryReducer.jsObjects { _ =>
        DBIO.successful(repo.getDroids(1000, 0).map(Json.toJson(_).as[JsObject]))
      }
      case ObjectField("Human", "name") => QueryReducer.mapped(_("name"))
    }: QueryMappings).orElse(rootMapping)

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

    val dbio = Compiler.compile(schema, query, resolvers)
    slickDb.run(dbio).map { queryResults =>
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
  }
}
