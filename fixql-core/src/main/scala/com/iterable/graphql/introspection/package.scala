package com.iterable.graphql

import play.api.libs.json.{Json, OWrites}

package object introspection {
  implicit val writesInputValue: OWrites[__InputValue] = Json.writes[__InputValue]
  implicit val writesDirective: OWrites[__Directive] = Json.writes[__Directive]
  implicit val writesEnumValue: OWrites[__EnumValue] = Json.writes[__EnumValue]
  implicit val writesField: OWrites[__Field] = Json.writes[__Field]
  implicit val writesType: OWrites[__Type] = Json.writes[__Type]
  implicit val writesSchema: OWrites[__Schema] = Json.writes[__Schema]
}
