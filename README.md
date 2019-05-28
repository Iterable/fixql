# FixQL
A Scala GraphQL implementation based on fixpoint data types.

badge

One of the goals of this project is to provide a GraphQL implementation that is small, easy-to-understand, and modular. Modularity means that orthogonal concerns e.g. authentication can be introduced in a generic way.

Our implementation combines the following:
- GraphQL-Java[1] to do query parsing and validation. But parsing then yields our own AST for execution.
- Fixpoint data types from the droste[2] library. Droste descends from the Matryoshka library.
- Query execution implemented as a fold that "compiles" the query AST into a Slick DBIO (for now). This is influenced by Greg Pfeil's talk on compilers using folds.[3]

This work is also inspired by the Sangria GraphQL scala library.

This implementation is under active development and is presently incomplete.

# Schemas and Resolvers

Schemas are defined with GraphQL-Java's GraphQLSchema[4] data type. Resolvers, in GraphQL terminology, fetch the data for a field. We define resolvers with:

```scala
trait Resolver[+A] {
  def resolveBatch: Seq[JsObject] => DBIO[Seq[A]]
}
```

As the GraphQL tutorial says, "You can think of each field in a GraphQL query as a function or method of the
previous type which returns the next type." (https://graphql.org/learn/execution/)

A non-batched `resolve` method would have signature `JsObject => DBIO[JsValue]`.
Since batching is more general and more performant, we use a batched
signature.

@return function from enclosing parent entity data to the data for this field */

# Mappings and Reducers

To associate resolvers with schemas, FixQL defines the following function signature:

Where Field.Annotated

This function signature can be understood as saying: For each field in the schema, this is the resolver. Also, the resolver can depend on the resolvers for the field's sub-fields, which were determined by the recursion.

Schemas and resolverss

Example:



The main function provided by the developer.

We refer to the 


But we define `case class QueryReducer(` so we actually have.

Future: Oh monads. Derivation. Type Safety. Builder DSL. Arguments. Fragments. Runtime polymorophism.

[1]: https://www.graphql-java.com/
[2]: https://github.com/higherkindness/droste
[4]: https://github.com/sellout/recursion-scheme-talk/blob/master/nanopass-compiler-talk.org