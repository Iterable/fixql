package com.iterable.graphql.derivation

import com.iterable.graphql.compiler.FieldTypeInfo.ObjectField
import com.iterable.graphql.compiler.{QueryMappings, QueryReducer}
import shapeless.ops.hlist.ToTraversable
import shapeless.ops.record.Keys
import shapeless.{HList, LabelledGeneric}

object DeriveMappings {
  class Derive[T](typeName: String) {
    def mappings[L <: HList, K <: HList](implicit gen: LabelledGeneric.Aux[T, L],
                 keys: Keys.Aux[L, K],
                 set: ToTraversable.Aux[K, Set, Symbol]): QueryMappings = {
      deriveMappings(typeName)
    }
  }

  def derive[T](typeName: String) = new Derive[T](typeName)

  def deriveMappings[T, L <: HList, K <: HList](TypeName: String)
  (implicit gen: LabelledGeneric.Aux[T, L],
   keys: Keys.Aux[L, K],
   set: ToTraversable.Aux[K, Set, Symbol]): QueryMappings = {
    val fieldNames = set.apply(keys.apply()).map(_.name)
    ({ case ObjectField(TypeName, fieldName) if fieldNames.contains(fieldName) =>
      QueryReducer.mapped(_(fieldName))
    }: QueryMappings)
  }
}
