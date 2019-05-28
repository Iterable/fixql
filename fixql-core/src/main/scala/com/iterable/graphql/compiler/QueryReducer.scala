package com.iterable.graphql.compiler

import com.iterable.graphql.Field
import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Writes
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext

object QueryReducer {
  def topLevelArrayWithSubfields(dbio: => DBIO[Seq[JsObject]])(implicit ec: ExecutionContext): QueryReducer[JsArray] = {
    jsObjects { _ =>
      dbio
    } // This is an "illegal" state since top-level must be a Seq with one element
      .mergeResolveSubfields
      .toTopLevelArray
  }

  def jsObjects(f: Seq[JsObject] => DBIO[Seq[JsObject]]): QueryReducer[JsObject] = QueryReducer[JsObject] { field: Field[Resolver[JsValue]] =>
    ResolverFn(field.name) { parents =>
      f(parents)
    }
  }

  def jsValues(f: Seq[JsObject] => DBIO[Seq[JsValue]]): QueryReducer[JsValue] = QueryReducer[JsValue] { field: Field[Resolver[JsValue]] =>
    ResolverFn(field.name) { parents =>
      f(parents)
    }
  }
}

/** Reduces a query field to a Resolver that fetches the data for the field. The
  * returned Resolver can depend on the (recursively generated) Resolvers for the subfields
  * of this field.
  */
case class QueryReducer[+A](reducer: Field[Resolver[JsValue]] => Resolver[A]) {
  def map[B](f: Seq[A] => Seq[B])(implicit ec: ExecutionContext): QueryReducer[B] = QueryReducer[B] { field =>
    val resolved = reducer(field)
    ResolverFn(resolved.jsonFieldName) { parents =>
      resolved.resolveBatch(parents).map(f)
    }
  }

  def flatMap[B](f: Field[Resolver[JsValue]] => Seq[A] => DBIO[Seq[B]])(implicit ec: ExecutionContext): QueryReducer[B] = QueryReducer[B] { field =>
    val resolved = reducer(field)
    ResolverFn(resolved.jsonFieldName) { parents =>
      resolved.resolveBatch(parents).flatMap(f(field))
    }
  }

  /** This should be private because its use implies that we have a QueryReducer in an "illegal" state
    * since Resolvers should always produce an output Seq that is parallel (and with the same size)
    * as the input Seq.
    */
  def toTopLevelArray(implicit ec: ExecutionContext, value: Writes[A]) = {
    map { objs =>
      Seq(Json.arr(objs.map(x => x: Json.JsValueWrapper): _*))
    }
  }

  /**
    * When this field is many-to-one from its parents, then this field's values just have
    * the type Seq[T] and can be directly passed into subfield resolvers and merged.
    */
  def mergeResolveSubfields(implicit ec: ExecutionContext, jsobjs: A <:< JsObject): QueryReducer[JsObject] = {
    flatMap { field => resolved =>
      for {
        _ <- DBIO.successful(())
        entityJsons = resolved.map(x => x: JsObject) // apply the implicit coercion from A <:< JsObject
        entityJsonsWithSubfieldsValues <- mergeResolveSubfields(entityJsons, field)
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
  def mergeResolveSubfieldsMany(implicit ec: ExecutionContext, subseqs: A <:< Seq[JsObject]) = QueryReducer[JsArray] { field =>
    val baseResolver = reducer(field)
    ResolverFn(baseResolver.jsonFieldName) { parents =>
      for {
        entitiesByParent <- baseResolver.resolveBatch(parents).map(_.map(x => x: Seq[JsObject]))
        allEntities = entitiesByParent.flatten
        allEntitiesWithSubfieldsValues <- mergeResolveSubfields(allEntities, field)
        mergedEntitiesByParent = reverseFlatten(entitiesByParent, allEntitiesWithSubfieldsValues)
      } yield {
        mergedEntitiesByParent.map(JsArray(_))
      }
    }
  }

  protected final def mergeResolveSubfields(entityJsons: Seq[JsObject], field: Field[Resolver[JsValue]])
    (implicit ec: ExecutionContext): DBIO[Seq[JsObject]] = {
    for {
      // for each subfield, the value for all rows
      subfieldsValues: Seq[Seq[(String, JsValue)]] <- DBIO.sequence(
        field.subfields.map { subfield =>
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
      val emptyJsons = Seq.fill(entityJsons.size)(Json.obj())
      subfieldsValues.foldLeft(emptyJsons) { (entityJsons, subfieldValues) =>
        (entityJsons zip subfieldValues).map { case (entityJson, subfieldValue) =>
          entityJson + subfieldValue
        }
      }
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
