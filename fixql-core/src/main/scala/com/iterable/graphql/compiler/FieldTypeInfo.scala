package com.iterable.graphql.compiler

import com.iterable.graphql.Field

case class FieldTypeInfo(parentTypeName: Option[String], typeName: String)

object FieldTypeInfo {

  /** Lets you match on a top-level field i.e. a field on the root query type
    *
    *   case FieldTypeInfo.RootField("humans") => ...
    */
  object TopLevelField {
    def unapply(x: (FieldTypeInfo, Field[_])): Option[String] = {
      Some(x).collect {
        case (FieldTypeInfo(None, _), Field(fieldName, _, _)) => fieldName
      }
    }
  }

  /** Lets you match on a particular field on a particular object type:
    *
    *   case FieldTypeInfo.ObjectField("Character", "name") => ...
    */
  object ObjectField {
    def unapply(x: (FieldTypeInfo, Field[_])): Option[(String, String)] = {
      Some(x).collect {
        case (FieldTypeInfo(Some(parentEntity), _), Field(fieldName, _, _)) => (parentEntity, fieldName)
      }
    }
  }
}
