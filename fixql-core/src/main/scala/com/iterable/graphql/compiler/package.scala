package com.iterable.graphql

import play.api.libs.json.JsValue

package object compiler {
  /** Maps a query field to a QueryReducer */
  type QueryMappings[F[_], T] = PartialFunction[(FieldTypeInfo, Field[Field.Annotated[FieldTypeInfo]]), QueryReducer[F, T]]
}
