package com.iterable.graphql.compiler

import cats._
import cats.implicits._
import com.iterable.graphql.Field
import org.typelevel.jawn.SimpleFacade
import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Writes

object QueryReducer {
  def mapped[F[_], T](f: JsObject => T)(implicit F: Applicative[F]): QueryReducer[F, T, T] = QueryReducer[F, T, T] { field: Field[Resolver[F, T]] =>
    ResolverFn(field.name) { parents =>
      F.pure(parents.map(f))
    }
  }

  def topLevelObjectsListWithSubfields[F[_] : Monad, T : SimpleFacade](dbio: => F[Seq[JsObject]]): QueryReducer[F, T, T] = {
    jsObjects { _ =>
      dbio
    } // This is an "illegal" state since top-level must be a Seq with one element
      .mergeResolveSubfields
      .toTopLevelArray
  }

  def jsObjects[F[_], T](f: Seq[JsObject] => F[Seq[JsObject]]): QueryReducer[F, T, JsObject] = QueryReducer[F, T, JsObject] { field: Field[Resolver[F, T]] =>
    ResolverFn(field.name) { parents =>
      f(parents)
    }
  }

  def jsValues[F[_], T](f: Seq[JsObject] => F[Seq[JsValue]]): QueryReducer[F, T, JsValue] = QueryReducer[F, T, JsValue] { field: Field[Resolver[F, T]] =>
    ResolverFn(field.name) { parents =>
      f(parents)
    }
  }
}

/** Reduces a query field to a Resolver that fetches the data for the field. The
  * returned Resolver can depend on the (recursively generated) Resolvers for the subfields
  * of this field.
  */
case class QueryReducer[F[_], T, A](reducer: Field[Resolver[F, T]] => Resolver[F, A]) {
  def as[B](implicit subJsValue: A <:< B, F: Functor[F]): QueryReducer[F, T, B] = {
    map(x => x: B)
  }

  def map[B](f: A => B)(implicit F: Functor[F]): QueryReducer[F, T, B] = QueryReducer[F, T, B] { field =>
    val resolved = reducer(field)
    ResolverFn(resolved.jsonFieldName) { parents =>
      resolved.resolveBatch(parents).map(_.map(f))
    }
  }

  def mapBatch[B](f: Seq[A] => Seq[B])(implicit F: Functor[F]): QueryReducer[F, T, B] = QueryReducer[F, T, B] { field =>
    val resolved = reducer(field)
    ResolverFn(resolved.jsonFieldName) { parents =>
      resolved.resolveBatch(parents).map(f)
    }
  }

  def flatMapBatch[B](f: Field[Resolver[F, T]] => Seq[A] => F[Seq[B]])(implicit F: Monad[F]): QueryReducer[F, T, B] = QueryReducer[F, T, B] { field =>
    val resolved = reducer(field)
    ResolverFn(resolved.jsonFieldName) { parents =>
      resolved.resolveBatch(parents).flatMap(f(field))
    }
  }

  /** This should be private because its use implies that we have a QueryReducer in an "illegal" state
    * since Resolvers should always produce an output Seq that is parallel (and with the same size)
    * as the input Seq.
    */
  def toTopLevelArray(implicit F: Functor[F], JSON: SimpleFacade[T], sub: A <:< T): QueryReducer[F, T, T] = {
    mapBatch { objs =>
      Seq(JSON.jarray(objs.map(x => x: T).toList))
    }
  }

  /**
    * When this field is many-to-one from its parents, then this field's values just have
    * the type Seq[T] and can be directly passed into subfield resolvers and merged.
    */
  def mergeResolveSubfields(implicit jsobjs: A <:< JsObject, F: Monad[F], JSON: SimpleFacade[T]): QueryReducer[F, T, T] = {
    flatMapBatch { field => resolved =>
      for {
        _ <- F.unit
        entityJsons = resolved.map(x => x: JsObject) // apply the implicit coercion from A <:< JsObject
        entityJsonsWithSubfieldsValues <- doMergeResolveSubfields(entityJsons, field)
      } yield {
        entityJsonsWithSubfieldsValues
      }
    }
  }

  /**
    * When this field is one-to-many from its parents, then this field's values will have
    * type Seq[Seq[T]] and must be flattened before being passed into subfield resolvers,
    * then unflattened before being merged.
    */
  def mergeResolveSubfieldsMany(implicit subseqs: A <:< Seq[JsObject], F: Monad[F], JSON: SimpleFacade[T]) = QueryReducer[F, T, JsArray] { field =>
    val baseResolver = reducer(field)
    ResolverFn(baseResolver.jsonFieldName) { parents =>
      for {
        entitiesByParent <- baseResolver.resolveBatch(parents).map(_.map(x => x: Seq[JsObject]))
        allEntities = entitiesByParent.flatten
        allEntitiesWithSubfieldsValues <- doMergeResolveSubfields(allEntities, field)
        mergedEntitiesByParent = reverseFlatten(entitiesByParent, allEntitiesWithSubfieldsValues)
      } yield {
        mergedEntitiesByParent.map(JsArray(_))
      }
    }
  }

  protected final def doMergeResolveSubfields(entityJsons: Seq[JsObject], field: Field[Resolver[F, T]])
    (implicit F: Applicative[F], JSON: SimpleFacade[T]): F[Seq[T]] = {
    for {
      // for each subfield, the value for all rows
      subfieldsValues: Seq[Seq[(String, T)]] <- Traverse[List].sequence(
        field.subfields.toList.map { subfield =>
          subfield.resolveBatch.apply(entityJsons).map(_.map(subfield.jsonFieldName -> _))
        }
      )
    } yield {
      /** We have a matrix with a row for each parent and a column for each subfield,
        * where each cell contains the value for that subfield in that parent.
        *
        * We foldLeft over the columns, starting with (a column of) empty Json objects
        * (the resulting Json contains values for only the selected fields). As we fold
        * over a new column of values, we merge it into the accumulated column of Json
        * objects.
        */
      val emptyJsons = Seq.fill(entityJsons.size)(Map.empty[String, T])
      val mergedJsons = subfieldsValues.foldLeft(emptyJsons) { (entityJsons, subfieldValues) =>
        (entityJsons zip subfieldValues).map { case (entityJson, subfieldValue) =>
          entityJson + subfieldValue
        }
      }
      mergedJsons.map(JSON.jobject)
    }
  }

  /**
    * Suppose you have a Seq[Seq[A]] which you then flatten into a Seq[A] with size N, then
    * map to a corresponding Seq[B] also with size N. Then this function gives you a back a
    * Seq[Seq[B]] structured the same way as the original Seq[Seq[A]].
    *
    * This is useful for doing batched joins.
    */
  private def reverseFlatten[T](original: Seq[Seq[_]], flattened: Seq[T]): Seq[Seq[T]] = {
    original.foldLeft((Seq.empty[Seq[T]], flattened)) { case ((accum, remainder), suborig) =>
      val (here, rest) = remainder.splitAt(suborig.size)
      (accum :+ here, rest)
    }._1
  }
}
