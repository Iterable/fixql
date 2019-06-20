package com.iterable.graphql.derivation

import com.iterable.graphql.SchemaDsl
import graphql.schema.GraphQLObjectType
import graphql.Scalars._
import graphql.schema.idl.SchemaPrinter
import org.scalatest.{FlatSpec, Matchers}

class IsGraphQLOutputTypeSpec extends FlatSpec with Matchers with SchemaDsl {

  case class Test(foo: String, bar: Int)

  "derivation" should "generate the expected object type" in {
    val objectType = new IsGraphQLOutputType.Derive[Test]("Test").toGraphQLObjectType

    val expected = GraphQLObjectType.newObject()
      .name("Test")
      .field(field("foo", GraphQLString))
      .field(field("bar", GraphQLInt))
      .build

    val printer = new SchemaPrinter(SchemaPrinter.Options.defaultOptions())
    printer.print(objectType) shouldEqual printer.print(expected)
  }
}
