package com.iterable.graphql.compiler

import cats.Id
import com.iterable.graphql.FromGraphQLJava.parseSchema
import com.iterable.graphql.compiler.FieldTypeInfo.{ObjectField, TopLevelField}
import com.iterable.graphql.{CharacterRepo, Field, FromGraphQLJava, Query, StarWarsSchema}
import org.scalatest.{FlatSpec, Matchers}
import org.typelevel.jawn.SimpleFacade
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

class CompilerSpec extends FlatSpec with Matchers with StarWarsSchema with ReducerHelpers {

  implicit val simpleFacade: SimpleFacade[JsValue] = org.typelevel.jawn.support.play.Parser.facade.asInstanceOf[SimpleFacade[JsValue]]

  "Compiler" should "compile" in {
    val graphQLSchema = parseSchema(starWarsSchema)
    val schema = FromGraphQLJava.toSchemaFunction(graphQLSchema)
    val repo = new CharacterRepo

    val resolvers = ({
      case TopLevelField("humans") => QueryReducer.topLevelObjectsListWithSubfields[Id, JsValue] {
        repo.getHumans(1000, 0).map(Json.toJson(_).as[JsObject])
      }
      case TopLevelField("droids") => QueryReducer.topLevelObjectsListWithSubfields[Id, JsValue] {
        repo.getDroids(1000, 0).map(Json.toJson(_).as[JsObject])
      }
      case ObjectField("Human", "id") => QueryReducer.mapped(_("id"))
      case ObjectField("Human", "name") => QueryReducer.mapped(_("name"))
    }: QueryMappings[Id, JsValue]).orElse(rootMapping)

    import qq.droste.syntax.fix._
    val query: Query[Field.Fixed] =
      Query(
        Seq(
          Field("humans",
            subfields = Seq(
              Field("id"),
              Field("name"),
            )
          ).fix
        )
      )

    val queryResults = Compiler.compile(schema, query, resolvers)
    val arr = (queryResults \ "humans").as[JsArray]
    arr.value.size shouldEqual repo.getHumans(1000, 0).size
    arr.value.head shouldEqual Json.obj(
      "id" -> "1000",
      "name" -> "Luke Skywalker",
    )
  }
}
