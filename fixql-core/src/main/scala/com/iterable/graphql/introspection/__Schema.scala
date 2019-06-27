package com.iterable.graphql.introspection

// 1-1 translation of
// https://github.com/graphql/graphql-spec/blob/master/spec/Section%204%20--%20Introspection.md
case class __Schema(
  types: Seq[__Type],
  queryType: __Type,
  mutationType: __Type,
  subscriptionType: __Type,
  directives: Seq[__Directive] = Nil,
)

case class __Type(
  kind: Enums.__TypeKind,
  name: String, // introspection schema says this is optional
  description: Option[String],
  fields: Seq[__Field],
  interfaces: Seq[__Type] = Nil,
  possibleTypes: Seq[__Type] = Nil,
  enumValues: Seq[__EnumValue] = Nil,
  inputFields: Seq[__InputValue] = Nil,
  ofType: Option[__Type] = None,
)

case class  __Field(
  name: String,
  description: Option[String],
  args: Seq[__InputValue] = Nil,
  `type`: __Type,
  isDeprecated: Boolean = false,
  deprecationReason: Option[String] = None,
)

case class  __InputValue(
  name: String,
  description: Option[String] = None,
  `type`: __Type,
  defaultValue: Option[String] = None,
)

case class __EnumValue(
  name:  String,
  description: Option[String],
  isDeprecated: Boolean,
  deprecationReason: Option[String],
)

// TBD
object Enums {
  type __TypeKind = String
  type __DirectiveLocation = String
}

case class __Directive(
  name: String,
  description: Option[String] = None,
  locations: Seq[Enums.__DirectiveLocation] = Nil,
  args: Seq[__InputValue] = Nil,
)