package com.iterable.graphql.compiler

import cats.{Applicative, Traverse}
import cats.implicits._
import com.iterable.graphql.Field
import com.iterable.graphql.compiler.FieldTypeInfo.ObjectField
import graphql.introspection.Introspection
import org.typelevel.jawn.SimpleFacade
import play.api.libs.json.Json

trait ReducerHelpers {
  protected final def standardMappings[F[_] : Applicative, T : SimpleFacade]: QueryMappings[F, T] = {
    rootMapping[F, T] orElse introspectionMappings[F, T]
  }

  /** Resolves the overall query by sequencing all the top-level resolvers.
    */
  protected final def rootMapping[F[_] : Applicative, T]
  (implicit JSON: SimpleFacade[T]): QueryMappings[F, T] = {
    case (FieldTypeInfo(None, ""), Field("", _, _)) => QueryReducer { field: Field[Resolver[F, T]] =>
      ResolverFn("") { containers =>
        for {
          subfieldsValues <- Traverse[List].sequence(field.subfields.toList.map { subfieldResolver =>
            subfieldResolver.resolveBatch.apply(containers)
              .map(_.head) // "parallel array" with the containers, but since we're at the root, we should only have one element
              .map(v => subfieldResolver.jsonFieldName -> v)
          })
        } yield {
          Seq(JSON.jobject(subfieldsValues.toMap))
        }
      }
    }
  }

  /** Resolves queries for "__typename"
    */
  protected final def introspectionMappings[F[_] : Applicative, T]
  (implicit JSON: SimpleFacade[T]): QueryMappings[F, T] = {
    val TypeNameField = Introspection.TypeNameMetaFieldDef.getName

    {
      case ObjectField(containingTypeName, TypeNameField) =>
        // TODO: the real implementation has to handle runtime polymorphism
        QueryReducer.mapped(_ => JSON.jstring(containingTypeName))
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
