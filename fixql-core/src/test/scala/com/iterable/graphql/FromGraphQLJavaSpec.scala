package com.iterable.graphql

import com.iterable.graphql.FromGraphQLJava.parseSchema
import io.circe.Json
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.json.{Json => PlayJson}

class FromGraphQLJavaSpec extends FlatSpec with Matchers {

  "FromGraphQLJava" should "parse documents" in {
    val graphQLSchema = parseSchema(starWarsSchema)
    val queryTry = FromGraphQLJava.parseAndValidateQuery(graphQLSchema, """{ h1: human(id: "1000") { name } h2: human(id: "1001") { name } }""", Json.obj())

    queryTry.get shouldEqual
      Query(
        Seq(
          Field("human", PlayJson.obj("id" -> "1000"), Seq(Field("name"))),
          Field("human", PlayJson.obj("id" -> "1001"), Seq(Field("name"))),
        ),
      )
  }

  val starWarsSchema =
    """
      |    schema {
      |        query: QueryType
      |    }
      |
      |    type QueryType {
      |        hero(episode: Episode): Character
      |        human(id : String) : Human
      |        droid(id: ID!): Droid
      |    }
      |
      |
      |    enum Episode {
      |        NEWHOPE
      |        EMPIRE
      |        JEDI
      |    }
      |
      |    interface Character {
      |        id: ID!
      |        name: String!
      |        friends: [Character]
      |        appearsIn: [Episode]!
      |    }
      |
      |    type Human implements Character {
      |        id: ID!
      |        name: String!
      |        friends: [Character]
      |        appearsIn: [Episode]!
      |        homePlanet: String
      |    }
      |
      |    type Droid implements Character {
      |        id: ID!
      |        name: String!
      |        friends: [Character]
      |        appearsIn: [Episode]!
      |        primaryFunction: String
      |    }
    """.stripMargin
}
