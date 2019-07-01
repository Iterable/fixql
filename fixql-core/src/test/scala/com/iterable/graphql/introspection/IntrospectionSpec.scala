package com.iterable.graphql.introspection

import com.iterable.graphql.FromGraphQLJava.parseSchema
import com.iterable.graphql.compiler.{Compiler, QueryMappings, ReducerHelpers}
import com.iterable.graphql.{FromGraphQLJava, StarWarsSchema}
import io.circe.Json
import org.scalatest.{FlatSpec, Matchers}

class IntrospectionSpec extends FlatSpec with Matchers with ReducerHelpers with StarWarsSchema {

  "introspection" should "query" in {
    val graphQLSchema = parseSchema(starWarsSchema)

    val mappings = standardMappings
    val (schema2, mappings2) = new IntrospectionMappings(graphQLSchema).newSchemaAndMappings(mappings)

    val query = FromGraphQLJava.parseAndValidateQuery(schema2, queryStr, Json.obj()).get

    val schema = FromGraphQLJava.toSchemaFunction(schema2, includeTopLevelSchemaMetaFields = true)
    val queryResults = Compiler.compile(schema, query, mappings2)
    println(queryResults)
  }

  // This is the query from graphiql
  val queryStr =
    """
      |query IntrospectionQuery {
      |       __schema {
      |         queryType { name }
      |         mutationType { name }
      |         subscriptionType { name }
      |         types {
      |           ...FullType
      |         }
      |         directives {
      |           name
      |           description
      |           locations
      |           args {
      |             ...InputValue
      |           }
      |         }
      |       }
      |     }
      |     fragment FullType on __Type {
      |       kind
      |       name
      |       description
      |       fields(includeDeprecated: true) {
      |         name
      |         description
      |         args {
      |           ...InputValue
      |         }
      |         type {
      |           ...TypeRef
      |         }
      |         isDeprecated
      |         deprecationReason
      |       }
      |       inputFields {
      |         ...InputValue
      |       }
      |       interfaces {
      |         ...TypeRef
      |       }
      |       enumValues(includeDeprecated: true) {
      |         name
      |         description
      |         isDeprecated
      |         deprecationReason
      |       }
      |       possibleTypes {
      |         ...TypeRef
      |       }
      |     }
      |     fragment InputValue on __InputValue {
      |       name
      |       description
      |       type { ...TypeRef }
      |       defaultValue
      |     }
      |     fragment TypeRef on __Type {
      |       kind
      |       name
      |       ofType {
      |         kind
      |         name
      |         ofType {
      |           kind
      |           name
      |           ofType {
      |             kind
      |             name
      |             ofType {
      |               kind
      |               name
      |               ofType {
      |                 kind
      |                 name
      |                 ofType {
      |                   kind
      |                   name
      |                   ofType {
      |                     kind
      |                     name
      |                   }
      |                 }
      |               }
      |             }
      |           }
      |         }
      |       }
      |     }
      |
    """.stripMargin
}
