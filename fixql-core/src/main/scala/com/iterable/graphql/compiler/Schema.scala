package com.iterable.graphql.compiler

/**
  * Abstracts the information we require from a schema implementation.
  */
trait Schema {
  /**
    * TODO: the result type can/should be generic, and should be an Option
    *
    * @param containingTypeName None for top-level fields
    * @return the name of the unwrapped type of this field. "unwrapped" means we see through lists and non-null
    */
  def getUnwrappedTypeNameOf(containingTypeName: Option[String], fieldName: String): String
}

/** A hard-coded schema for testing
  *
  * @param types map from typeName -> fieldName -> typeName
  * @param topLevelFields map from fieldName -> typeName for all top-level fields
  */
case class MapSchema(types: Map[String, Map[String, String]], topLevelFields: Map[String, String]) extends Schema {
  self =>

  def getTypeNameOfField(parentTypeName: String, fieldName: String): String = {
    types(parentTypeName)(fieldName)
  }

  def getTypeNameOf(parentTypeName: Option[String], fieldName: String): String = {
    parentTypeName
      .map(getTypeNameOfField(_, fieldName))
      .getOrElse(topLevelFields(fieldName))
  }

  override def getUnwrappedTypeNameOf(parentTypeName: Option[String], fieldName: String) = {
    self.getTypeNameOf(parentTypeName, fieldName)
  }
}
