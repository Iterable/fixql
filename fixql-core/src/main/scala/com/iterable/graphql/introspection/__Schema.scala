package com.iterable.graphql.introspection

import play.api.libs.json.{Json, OWrites}

// 1-1 translation of
// https://github.com/graphql/graphql-spec/blob/master/spec/Section%204%20--%20Introspection.md
case class __Schema(
  types: Seq[__Type],
  queryType: __Type,
  mutationType: Option[__Type] = None,
  subscriptionType: Option[__Type] = None,
  directives: Seq[__Directive] = Nil,
)

object __Schema {
  implicit val writes: OWrites[__Schema] = Json.writes[__Schema]
}

case class __Type(
  kind: Enums.__TypeKind,
  name: String, // introspection schema says this is optional
  description: Option[String],
  fields: Seq[__Field] = Nil,
  interfaces: Seq[__Type] = Nil,
  possibleTypes: Seq[__Type] = Nil,
  enumValues: Seq[__EnumValue] = Nil,
  inputFields: Seq[__InputValue] = Nil,
  ofType: Option[__Type] = None,
)

object __Type {
  implicit val writes: OWrites[__Type] = Json.writes[__Type]
}

case class  __Field(
  name: String,
  description: Option[String],
  args: Seq[__InputValue] = Nil,
  `type`: __Type,
  isDeprecated: Boolean = false,
  deprecationReason: Option[String] = None,
)

object __Field  {
  implicit val writes: OWrites[__Field] = Json.writes[__Field]
}

case class  __InputValue(
  name: String,
  description: Option[String] = None,
  `type`: __Type,
  defaultValue: Option[String] = None,
)

object __InputValue {
  implicit val writes: OWrites[__InputValue] = Json.writes[__InputValue]
}

case class __EnumValue(
  name:  String,
  description: Option[String],
  isDeprecated: Boolean,
  deprecationReason: Option[String],
)

object __EnumValue {
  implicit val writes: OWrites[__EnumValue] = Json.writes[__EnumValue]
}

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

object __Directive {
  implicit val writes: OWrites[__Directive] = Json.writes[__Directive]
}