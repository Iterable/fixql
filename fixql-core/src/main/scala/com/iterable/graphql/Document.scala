package com.iterable.graphql

import cats.Functor
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import higherkindness.droste.data.Attr
import higherkindness.droste.data.Fix

/** Simplified AST for "reduced" documents: variables have been interpolated,
  * and fragments have been inlined
  *
  * This is unused today but may be required when we introduce support for mutations.
  */
case class Document[T](
  query: Query[T]
)

case class Query[A](
  fields: Seq[A]
) {
  // TODO: move to sealed trait or let Query == Root
  def fieldTreeRoot = Field("", subfields = fields)

  def map[B](f: A => B) = {
    Query(fields.map(f))
  }
}

/**
  * @param arguments we use a JsObject because we may want to support case class deserialization here
  *                  and JSON codecs capture the reality of what's being passed
  */
case class Field[A](
  name: String,
  arguments: JsObject = Json.obj(),
  subfields: Seq[A] = Nil,
) {
  def map[B](f: A => B): Field[B] = {
    Field(name, arguments, subfields.map(f))
  }
}

object Field {
  type Fixed = Fix[Field]
  type Annotated[A] = Attr[Field, A]

  implicit val functor: Functor[Field] = new Functor[Field] {
    override def map[A, B](fa: Field[A])(f: A => B) = fa.map(f)
  }
}
