package com.iterable.graphql.compiler

import com.iterable.graphql.Field
import com.iterable.graphql.compiler.FieldTypeInfo.ObjectField
import graphql.introspection.Introspection
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext

trait ReducerHelpers {
  protected final def standardMappings(implicit ec: ExecutionContext): QueryMappings = {
    rootMapping orElse introspectionMappings
  }

  /** Resolves the overall query by sequencing all the top-level resolvers.
    */
  protected final def rootMapping(implicit ec: ExecutionContext): QueryMappings = {
    case (FieldTypeInfo(None, ""), Field("", _, _)) => QueryReducer { field: Field[Resolver[JsValue]] =>
      ResolverFn("") { containers =>
        for {
          subfieldsValues <- DBIO.sequence(field.subfields.map { subfieldResolver =>
            subfieldResolver.resolveBatch.apply(containers)
              .map(_.head) // "parallel array" with the containers, but since we're at the root, we should only have one element
              .map(v => subfieldResolver.jsonFieldName -> (v: Json.JsValueWrapper))
          })
        } yield {
          Seq(Json.obj(subfieldsValues: _*))
        }
      }
    }
  }

  /** Resolves queries for "__typename"
    */
  protected final def introspectionMappings(implicit ec: ExecutionContext): QueryMappings = {
    val TypeNameField = Introspection.TypeNameMetaFieldDef.getName

    {
      case ObjectField(containingTypeName, TypeNameField) => QueryReducer.jsValues {
        containers =>
          // TODO: the real implementation has to handle runtime polymorphism
          DBIO.successful(containers.map(_ => JsString(containingTypeName)))
      }
    }
  }

  /**
    * Given a collection `coll` of elements of type T, a function key: T => K
    * that yields the (not-necessarily-unique) key of each element, and a
    * sequence of those keys (which may well have duplicates), return a sequence of
    * T corresponding to the input keySequence.
    */
  protected final def resequence[T, K](coll: Seq[T], key: T => K, keySequence: Seq[K]) = {
    val map = coll.groupBy(key).mapValues(_.head)
    keySequence.map(k => map(k))
  }
}
