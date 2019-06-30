package com.iterable.graphql.compiler

import cats.{Applicative, Monad}
import com.iterable.graphql.Field
import org.typelevel.jawn.SimpleFacade
import play.api.libs.json.{JsObject, JsValue}

trait QueryReducerHelpers[T] {
  def mapped[F[_], A](f: JsObject => A)(implicit F: Applicative[F]): QueryReducer[F, T, A] = QueryReducer[F, T, A] { field: Field[Resolver[F, T]] =>
    ResolverFn(field.name) { parents =>
      F.pure(parents.map(f))
    }
  }

  def topLevelObjectsListWithSubfields[F[_] : Monad]
  (dbio: => F[Seq[JsObject]])
  (implicit JSON: SimpleFacade[T]): QueryReducer[F, T, T] = {
    jsObjects { _ =>
      dbio
    } // This is an "illegal" state since top-level must be a Seq with one element
      .mergeResolveSubfields
      .toTopLevelArray
  }

  def jsObjects[F[_]](f: Seq[JsObject] => F[Seq[JsObject]]): QueryReducer[F, T, JsObject] = QueryReducer[F, T, JsObject] { field: Field[Resolver[F, T]] =>
    ResolverFn(field.name) { parents =>
      f(parents)
    }
  }

  def jsValues[F[_]](f: Seq[JsObject] => F[Seq[JsValue]]): QueryReducer[F, T, JsValue] = QueryReducer[F, T, JsValue] { field: Field[Resolver[F, T]] =>
    ResolverFn(field.name) { parents =>
      f(parents)
    }
  }
}
