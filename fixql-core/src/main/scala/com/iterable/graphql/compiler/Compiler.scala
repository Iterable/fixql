package com.iterable.graphql.compiler

import com.iterable.graphql.Field
import com.iterable.graphql.Query
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import qq.droste.data.Attr
import qq.droste.data.Fix
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext

object Compiler {

  /** Given a schema and a query represented as a Field tree, returns a new Field tree annotated with the
    * (unwrapped) type of each field.
    */
  private def annotateWithTypeInfo(schema: Schema, query: Query[Field.Fixed]): Query[Field.Annotated[FieldTypeInfo]] = {
    def helper(parentEntity: Option[String], field: Field.Fixed): Attr[Field, FieldTypeInfo] = {
      val theField = Fix.un[Field](field)
      val typeName = schema.getUnwrappedTypeNameOf(parentEntity, theField.name)
      assert(typeName != null)
      val fieldInfo = FieldTypeInfo(parentEntity, typeName)
      Attr(fieldInfo, theField.map(helper(Some(typeName), _)))
    }

    query.map(helper(None, _))
  }

  /** Given a schema, query, and mappings that specify resolvers for each field in the schema,
    * generates the root resolver, then runs it.
    * @return an object of the query type
    */
  def compile(schema: Schema, query: Query[Field.Fixed], mappings: QueryMappings)
    (implicit ec: ExecutionContext): DBIO[JsObject] = {
    val annotated: Query[Field.Annotated[FieldTypeInfo]] = annotateWithTypeInfo(schema, query)

    val mappingsFn = toMappingFunction(mappings)

    val annotatedRoot: Field.Annotated[FieldTypeInfo] = Attr(FieldTypeInfo(None, "") -> annotated.fieldTreeRoot)
    val rootResolver = annotatedFold[FieldTypeInfo, Resolver[JsValue]](mappingsFn)(annotatedRoot)
    
    // The root resolvers are applied with a singleton list containing an empty Json object
    // as the set of parents
    val containersAtRoot = Seq(Json.obj())
    val dbio = rootResolver.resolveBatch.apply(containersAtRoot).map(_.head.as[JsObject])
    dbio
  }

  private def toMappingFunction(mappings: QueryMappings): Field.Annotated[FieldTypeInfo] => Field[Resolver[JsValue]] => Resolver[JsValue] = {
    annotatedField => field =>
      val (typeInfo, fld) = Attr.un[Field, FieldTypeInfo](annotatedField)
      mappings((typeInfo, fld)).reducer(field)
  }

  /** Applies the provided function recursively over the tree
    */
  def annotatedFold[A, B](f: Field.Annotated[A] => Field[B] => B): Field.Annotated[A] => B = { attr =>
    def helper(node: Field.Annotated[A]): B = {
      // TODO: reintroduce tail method
      val sub = Attr.un[Field, A](node)._2.map(helper) // recurse
      f(node)(sub)
    }
    helper(attr)
  }
}
