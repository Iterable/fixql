package com.iterable.graphql.derivation

import com.iterable.graphql.compiler.FieldTypeInfo.ObjectField
import com.iterable.graphql.compiler.{QueryMappings, QueryReducer}
import shapeless.ops.hlist.{SelectAll, ToTraversable}
import shapeless.ops.record.Keys
import shapeless.{HList, LabelledGeneric}

object DeriveMappings {
  class Derive[T](TypeName: String) {
    def allFields[L <: HList, K <: HList](implicit gen: LabelledGeneric.Aux[T, L],
                                          keys: Keys.Aux[L, K],
                                          set: ToTraversable.Aux[K, Set, Symbol]): QueryMappings = {
      deriveMappings(TypeName)
    }

    /** Only include the selected fields in the generated mappings. If you want to
      * customize anything about a field mapping, you should simply define the
      * field's mapping manually rather than using automatic derivation.
      */
    def selected[L <: HList, K <: HList, S <: HList]
    (selections: S)
    (implicit gen: LabelledGeneric.Aux[T, L],
     keys: Keys.Aux[L, K],
     select: SelectAll[K, S],
     set: ToTraversable.Aux[S, Set, Symbol]): QueryMappings = {
      val fieldNames = set.apply(select.apply(keys.apply())).map(_.name)
      fieldMappings(TypeName, fieldNames)
    }
  }

  def derive[T](typeName: String) = new Derive[T](typeName)

  def deriveMappings[T, L <: HList, K <: HList](TypeName: String)
  (implicit gen: LabelledGeneric.Aux[T, L],
   keys: Keys.Aux[L, K],
   set: ToTraversable.Aux[K, Set, Symbol]): QueryMappings = {
    val fieldNames = set.apply(keys.apply()).map(_.name)
    fieldMappings(TypeName, fieldNames)
  }

  private def fieldMappings(TypeName: String, fieldNames: Set[String]) = {
    ({ case ObjectField(TypeName, fieldName) if fieldNames.contains(fieldName) =>
      QueryReducer.mapped(_(fieldName))
    }: QueryMappings)
  }
}
