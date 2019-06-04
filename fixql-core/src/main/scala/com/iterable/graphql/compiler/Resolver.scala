package com.iterable.graphql.compiler

import play.api.libs.json.JsObject
import slick.dbio.DBIO

/** A trait with case class subclasses lets us optimize later */
trait Resolver[+A] {
  def jsonFieldName: String

  /** As the GraphQL tutorial says, "You can think of each field in a GraphQL query as a function or method of the
    * previous type which returns the next type." (https://graphql.org/learn/execution/)
    *
    * A non-batched `resolve` method would have signature JsObject => DBIO[JsValue].
    * Since batching is more general and more performant, we use a batched
    * signature below.
    *
    * @return function from containing object data to the data for this field returned as a sequence
    *         parallel with the input sequence
    */
  def resolveBatch: Seq[JsObject] => DBIO[Seq[A]]
}

case class ResolverFn[A](jsonFieldName: String)(val resolveBatch: Seq[JsObject] => DBIO[Seq[A]]) extends Resolver[A]
