# FixQL
[![Build Status](https://travis-ci.org/Iterable/fixql.svg?branch=master)](https://travis-ci.org/Iterable/fixql)

A Scala GraphQL implementation based on fixpoint data types. This project aims to provide a GraphQL implementation that is small, easy-to-understand, and modular. Our implementation combines the following:
- [GraphQL-Java][1] to do query parsing and validation. Parsing is modified to yield our own AST for execution.
- Fixpoint data types from the [droste][2] library. Droste descends from the Matryoshka library.
- Query execution implemented as a fold that "compiles" the query AST into a Slick DBIO (for now). This is influenced by Greg Pfeil's talk on [compiling with recursion schemes][3].

This work is also inspired by the [Sangria][4] GraphQL Scala library.

This project is under active development and is presently incomplete.

## Schemas and Resolvers

Schemas are defined with GraphQL-Java's [GraphQLSchema][5] data type. Resolvers, in GraphQL terminology, fetch the data for a field. We define resolvers with:

```scala
trait Resolver[+A] {
  def resolveBatch: Seq[JsObject] => DBIO[Seq[A]]
}
```

As the GraphQL tutorial says, "You can think of each field in a GraphQL query as a function or method of the previous type which returns the next type." (https://graphql.org/learn/execution/) A non-batched `resolve` method would have signature `JsObject => DBIO[JsValue]` where the `JsObject` represents the data for the object that contains the field being fetched. Since batching is more general and more performant, we use a batched signature. Thus, `resolveBatch`  depends on a `Seq[JsObject]` -- the data for the batch of containing objects. The resolver then fetches the data for the field directly out of the objects, or by performing an additional database query.

## Query Execution AST

A GraphQL query is represented as a tree of fields. Ignoring aliases, arguments, and fragments we have:

```scala
case class Field[A](name: String, subfields: Seq[A])
```

## Mappings and Reducers

To associate resolvers with schemas, FixQL's query compiler takes a function that maps each field of the schema to a resolver:
```
  Field.Annotated[FieldTypeInfo] => Resolver[JsValue]
```

where `Field.Annotated[FieldTypeInfo]` is a field annotated with with some additional information that indicate the field's containing object type. The resolver can depend on the resolvers for the field's sub-fields, which have been determined recursively. So we actually have:

```
  Field.Annotated[FieldTypeInfo] => Field[Resolver[JsValue]] => Resolver[JsValue]
```

We refer to the entire function signature above as "mappings". And we define a wrapper type for the latter function:
 
```scala
case class QueryReducer[+A](reducer: Field[Resolver[JsValue]] => Resolver[A])
```

Finally, we allow the developer to define the mappings in pieces so ultimately we have a partial function:

```scala
  type QueryMappings = PartialFunction[(FieldTypeInfo, Field[_]), QueryReducer[JsValue]]
```

Example:

```scala
      case ObjectField("Human", "name") => QueryReducer.jsValues { parents =>
        DBIO.successful(parents.map(_.apply("name")))
      }
```

where `ObjectField` is an extractor that matches the `name` field on the `Human` object type.

## Compilation and Execution

Putting this all together, invoking FixQL's query compiler looks like:

```scala
val dbio = Compiler.compile(schema, mappings, query)
```

This yields a `DBIO[JsObject]` that the compiler forms through the following transformation phases:
- Parse the query into the execution AST (query field tree)
- Annotate the field tree with some type information drawn from the schema, in particular, the containing object type for each field
- Apply the mappings recursively to the AST: generate a Resolver for each node of the tree, from the bottom up. This step yields a `Field[Resolver[JsObject]]` i.e. a tree of Resolvers.
- "Run" the resolvers from the top-down, passing the data from a parent node into the child node as the containing object data. This step yields one large `DBIO[JsObject]`.

The caller may then actually run the resulting DBIO using a Slick Database instance.

TBD: Monads. Optimization. Derivation. Type Safety. Builder DSL. Arguments. Fragments. Runtime polymorphism.

[1]: https://www.graphql-java.com/
[2]: https://github.com/higherkindness/droste
[3]: https://github.com/sellout/recursion-scheme-talk/blob/master/nanopass-compiler-talk.org
[4]: https://sangria-graphql.org/
[5]: https://www.graphql-java.com/documentation/v12/schema/
