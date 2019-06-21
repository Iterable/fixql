package com.iterable.graphql.derivation

import com.iterable.graphql.{SchemaAndMappingsMutableBuilderDsl, SchemaDsl}
import graphql.schema.GraphQLObjectType
import graphql.Scalars._
import graphql.schema.idl.SchemaPrinter
import org.scalatest.{FlatSpec, Matchers}

class IsGraphQLOutputTypeSpec extends FlatSpec with Matchers
  with SchemaAndMappingsMutableBuilderDsl {

  case class Test(foo: String, bar: Int)

  "object type derivation" should "generate the expected object type" in {
    val objectType = ToGraphQLType.derive[Test]("Test").toGraphQLObjectType

    val expected = GraphQLObjectType.newObject()
      .name("Test")
      .field(field("foo", GraphQLString))
      .field(field("bar", GraphQLInt))
      .build

    val printer = new SchemaPrinter(SchemaPrinter.Options.defaultOptions())
    printer.print(objectType) shouldEqual printer.print(expected)
  }

  "mappings derivation" should "execute correctly" in {
    case class Human(id: Long, name: String)

    val (schema, mappings) =
      schemaAndMappings { implicit builders =>
        withQueryType { implicit obj =>
          field("humans", list(humanType))
        }

        lazy val humanType =
          ToGraphQLType.derive[Human]("Human").toGraphQLObjectType
        addMappings(DeriveMappings.derive[Human]("Human").mappings)
      }
  }
}
