package com.iterable.graphql.derivation

import com.iterable.graphql.compiler.FieldTypeInfo.ObjectField
import com.iterable.graphql.compiler.{QueryMappings, QueryReducer}
import shapeless.ops.hlist.ToTraversable
import shapeless.ops.record.Keys
import shapeless.{HList, LabelledGeneric}

object DeriveMappings {
  def deriveMappings[T, L <: HList, K <: HList](typeName: String)
  (implicit gen: LabelledGeneric.Aux[T, L],
   keys: Keys.Aux[L, K],
   set: ToTraversable.Aux[K, Set, Symbol]): QueryMappings = {
    val ObjectName = typeName
    val fieldNames = set.apply(keys.apply()).map(_.name)
    ({ case ObjectField(ObjectName, fieldName) if fieldNames.contains(fieldName) =>
      QueryReducer.mapped(_(fieldName))
    }: QueryMappings)
  }
}
