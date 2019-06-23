package com.iterable.graphql.derivation

import cats.Monad
import com.iterable.graphql.compiler.FieldTypeInfo.ObjectField
import com.iterable.graphql.compiler.{QueryMappings, QueryReducer}
import play.api.libs.json.{JsNull, JsValue}
import shapeless.ops.hlist.{SelectAll, ToTraversable}
import shapeless.ops.record.Keys
import shapeless.{HList, LabelledGeneric, SingletonProductArgs}

/**
  * Generates trivial mappings for the fields of a case class.
  *
  * The generated mappings are trivial in the sense that they simply extract the field's
  * value from the containing object using QueryReducer.mapped.
  *
  * For the non-trivial cases one should simply define the mappings explicitly.
  */
object DeriveMappings {
  def apply[T](typeName: String) = new Derive[T](typeName)

  class Derive[T](TypeName: String) extends SingletonProductArgs {
    def allFields[F[_] : Monad, L <: HList, K <: HList]
    (implicit gen: LabelledGeneric.Aux[T, L],
     keys: Keys.Aux[L, K],
     set: ToTraversable.Aux[K, Set, Symbol]): QueryMappings[F] = {
      val fieldNames = set.apply(keys.apply()).map(_.name)
      fieldMappings(TypeName, fieldNames)
    }

    /** Only include the selected fields in the generated mappings. If you want to
      * customize anything about a field mapping, you should exclude the field
      * from automatic derivation and simply define the field's mapping explicitly.
      */
    def fieldsProduct[F[_] : Monad, L <: HList, K <: HList, S <: HList]
    (selections: S)
    (implicit gen: LabelledGeneric.Aux[T, L],
     keys: Keys.Aux[L, K],
     select: SelectAll[K, S],
     set: ToTraversable.Aux[S, Set, Symbol]): QueryMappings[F] = {
      val fieldNames = set.apply(select.apply(keys.apply())).map(_.name)
      fieldMappings(TypeName, fieldNames)
    }
  }

  private def fieldMappings[F[_] : Monad](TypeName: String, fieldNames: Set[String]) = {
    ({ case ObjectField(TypeName, fieldName) if fieldNames.contains(fieldName) =>
      QueryReducer.mapped { parent =>
        // If the parent has defined this field as an Option, then Json serialization
        // (of the data-fetcher for the parent) will omit the field.
        // But if the field has been selected then we'll try to get the field and fail.
        // In that case, I believe the field should be re-produced into our query
        // result JSON (any field selected in the query should always be present in
        // the results?) but with a JS value of null. We should confirm this with the
        // GraphQL spec.
        (parent \ fieldName).asOpt[JsValue].getOrElse(JsNull)
      }
    }: QueryMappings[F])
  }
}
