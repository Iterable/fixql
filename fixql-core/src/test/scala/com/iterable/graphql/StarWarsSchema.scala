package com.iterable.graphql

trait StarWarsSchema {
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
      |        humans: [Human]
      |        droids: [Droid]
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
