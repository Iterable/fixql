package com.iterable.graphql.derivation

import com.iterable.graphql.{MutableMappingsBuilder, SchemaAndMappingsMutableBuilderDsl}
import graphql.schema.{GraphQLObjectType, GraphQLOutputType}
import shapeless.ops.hlist
import shapeless.{HList, LabelledGeneric}
import shapeless.ops.hlist.{ToTraversable, ZipWithKeys}
import shapeless.ops.record.{Keys, SelectAll, ToMap}

/**
  * Adds derivation-based syntax to the builder DSL.
  * See [[DerivationSpec]] for example usage.
  */
trait DerivationBuilderDsl extends SchemaAndMappingsMutableBuilderDsl {
  self =>

  protected final def derive[T]
  (implicit obj: GraphQLObjectType.Builder)= {
    new Derive[T](obj.build.getName)
  }

  class Derive[T](name: String)(implicit obj: GraphQLObjectType.Builder) {
    def addFieldsAndMappings[L <: HList, O <: HList, MV <: HList, S <: HList, V <: HList, L2 <: HList, K <: HList]
    (selections: S)
    (implicit gen: LabelledGeneric.Aux[T, L],
     select: SelectAll.Aux[L, S, V],
     zipped: ZipWithKeys.Aux[S, V, L2],
     mapValues: MapValuesNull.Aux[ToGraphQLType.type, L2, MV],
     toMap: ToMap.Aux[MV, _, GraphQLOutputType],
    // Simply concatenate the necessary implicits for both functions, somewhat redundantly
     mappings: MutableMappingsBuilder,
     keys: Keys.Aux[L, K],
     selectKeys: hlist.SelectAll[K, S],
     set: ToTraversable.Aux[S, Set, Symbol]
    )  = {
      addFields(selections)
      addMappings(selections)
    }

    def addFields[L <: HList, O <: HList, MV <: HList, S <: HList, V <: HList, L2 <: HList]
    (selections: S)
    (implicit gen: LabelledGeneric.Aux[T, L],
     select: SelectAll.Aux[L, S, V],
     zipped: ZipWithKeys.Aux[S, V, L2],
     mapValues: MapValuesNull.Aux[ToGraphQLType.type, L2, MV],
     toMap: ToMap.Aux[MV, _, GraphQLOutputType]
    )  = {
      val derived = DeriveGraphQLType[T](name).fields(selections)
      obj.fields(derived.getFieldDefinitions)
    }

    def addMappings[L <: HList, K <: HList, S <: HList]
    (selections: S)
    (implicit mappings: MutableMappingsBuilder,
     gen: LabelledGeneric.Aux[T, L],
     keys: Keys.Aux[L, K],
     select: hlist.SelectAll[K, S],
     set: ToTraversable.Aux[S, Set, Symbol])= {
      val derived = DeriveMappings[T](name).fields(selections)
      self.addMappings(derived)
    }
  }
}
