package com.iterable.graphql

import java.util.concurrent.CompletableFuture

import com.iterable.graphql.compiler.Schema
import graphql.ExecutionInput
import graphql.Scalars
import graphql.execution.ExecutionContext
import graphql.execution.ExecutionStepInfo
import graphql.execution.FetchedValue
import graphql.execution.MergedField
import graphql.execution.ValuesResolver
import graphql.execution.nextgen.ExecutionStrategy
import graphql.execution.nextgen.FetchedValueAnalysis
import graphql.execution.nextgen.FieldSubSelection
import graphql.execution.nextgen.result.ExecutionResultNode
import graphql.execution.nextgen.result.LeafExecutionResultNode
import graphql.execution.nextgen.result.RootExecutionResultNode
import graphql.introspection.Introspection
import graphql.language.{Field => JField}
import graphql.schema.DataFetchingFieldSelectionSet
import graphql.schema.DataFetchingFieldSelectionSetImpl
import graphql.schema.GraphQLTypeUtil
import graphql.schema.idl.EchoingWiringFactory
import graphql.schema.idl.SchemaGenerator
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaParser
import io.circe.Json
import io.circe.JsonNumber
import io.circe.JsonObject
import play.api.libs.json.{Json => PlayJson}
import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsString
import play.api.libs.json.JsValue

import scala.concurrent.Future
import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try

/**
  * Document and schema parsing using the GraphQL Java library.
  */
object FromGraphQLJava {

  /** Parse and validate documents (queries and mutations) using the GraphQL Java library.
    * This requires a GraphQL Java GraphQLSchema. Returns our Query type.
    *
    * Overall flow looks as follows:
    * - Build a GraphQL Java GraphQLSchema whether from SDL or programmatically.
    * - Use GraphQL Java to parse the document (i.e. query) into Java AST
    * - Use GraphQL Java to resolve variables etc.
    * - Convert to Scala AST by traversing Java using selection-set peeking. This is what the code below does.
    * - Compile Scala AST into DBIO
    * - Run DBIO
    *
    * This method uses "field selection sets" from GraphQL-Java to extract a Field tree for the query:
    * https://www.graphql-java.com/documentation/v12/fieldselection/
    */
  def parseAndValidateQuery(graphQLSchema: GraphQLSchema, query: String, variables: Json): Try[Query[Field.Fixed]] = {
    val extractExecutionStrategy = new ContextExtractingExecutionStrategy
    Try {
      val (context: ExecutionContext, fieldSubSelection: FieldSubSelection) = graphql.nextgen.GraphQL.newGraphQL(graphQLSchema)
        .executionStrategy(extractExecutionStrategy)
        .build()
        .execute(ExecutionInput.newExecutionInput(query)
          .variables(toJavaValues(variables).asInstanceOf[Map[String, AnyRef]].asJava)
          .build())
        .getData[java.util.LinkedHashMap[String, Any]]
        .get(null) // the map will have a value with key = null

      val valuesResolver = new ValuesResolver
      val topLevelFields: Seq[Field.Fixed] =
        fieldSubSelection.getMergedSelectionSet.getSubFieldsList.asScala.map { mergedField =>
          // See DataFetchingSelectionSetImpl.traverseFields
          val fieldDef = Introspection.getFieldDef(graphQLSchema, graphQLSchema.getQueryType, mergedField.getName)
          val unwrappedType = GraphQLTypeUtil.unwrapAll(fieldDef.getType)
          // See ValueFetcher.fetchValue() or other callers of DataFetchingFieldSelectionSetImpl.newCollector()
          //val codeRegistry: GraphQLCodeRegistry = executionContext.getGraphQLSchema.getCodeRegistry
          //val parentType: GraphQLFieldsContainer = getFieldsContainer(executionInfo)
          //val argumentValues: util.Map[String, AnyRef] = valuesResolver.getArgumentValues(codeRegistry, fieldDef.getArguments, field.getArguments, executionContext.getVariables)
          //val fieldDef: GraphQLFieldDefinition = executionInfo.getFieldDefinition
          //val fieldType: GraphQLOutputType = fieldDef.getType
          val argumentValues = valuesResolver.getArgumentValues(fieldDef.getArguments, mergedField.getArguments, context.getVariables)
          val selectionSet = DataFetchingFieldSelectionSetImpl.newCollector(context, unwrappedType, mergedField)
          mkField(mergedField.getName, argumentValues, selectionSet)
        }.toSeq
      Query[Field.Fixed](topLevelFields)
    }
  }

  private def toJavaValues(value: Json): AnyRef = {
    value.foldWith(new Json.Folder[AnyRef] {
      override def onNull = null
      override def onBoolean(value: Boolean) = value: java.lang.Boolean
      override def onNumber(value: JsonNumber) = value.toBigDecimal
      override def onString(value: String) = value
      override def onArray(value: Vector[Json]) = value.map(toJavaValues)
      override def onObject(value: JsonObject) = value.toMap.mapValues(toJavaValues)
    })
  }

  import qq.droste.syntax.fix._
  private def mkField(fieldName: String, arguments: java.util.Map[String, AnyRef], fss: DataFetchingFieldSelectionSet): Field.Fixed = {
    // getFields contains flattened fields from all children, but we only want the immediate children
    val childFields = fss.getFields.asScala.filterNot(_.getQualifiedName.contains("/"))
    val args = PlayJson.obj(arguments.asScala.map { case (key, value) =>
      key -> (fromJavaValue(value): play.api.libs.json.Json.JsValueWrapper)
    }.toSeq: _*)
    Field[Field.Fixed](
      fieldName,
      args,
      childFields.map { selectedField =>
        val subfss = selectedField.getSelectionSet
        mkField(selectedField.getName, selectedField.getArguments, subfss)
      }.toSeq,
    ).fix
  }

  private def fromJavaValue(value: Any): JsValue = {
    value match {
      case x if x == null => JsNull
      case f: Float => JsNumber(BigDecimal.decimal(f))
      case s: String => JsString(s)
      case n: Int => JsNumber(n)
      case n: Long => JsNumber(n)
      case f: Boolean => JsBoolean(f)
      case rg: Array[_] => JsArray(rg.map(fromJavaValue))
    }
  }

  /**
    * Create a GraphQL Java GraphQLSchema from a GraphQL SDL String.
    */
  def parseSchema(schemaStr: String): GraphQLSchema = {
    val schemaParser = new SchemaParser
    val schemaGenerator = new SchemaGenerator

    val typeRegistry = schemaParser.parse(schemaStr)
    val wiring = RuntimeWiring.newRuntimeWiring().wiringFactory(new EchoingWiringFactory).build()
    val graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, wiring)
    graphQLSchema
  }

  def toSchemaFunction(schema: GraphQLSchema): Schema = new Schema {
    override def getUnwrappedTypeNameOf(parentTypeName: Option[String], fieldName: String) = {
      // TODO: there should be a better way to handle this. Maybe using TraversalContext.getFieldDef
      if (fieldName == Introspection.TypeNameMetaFieldDef.getName) {
        // This doesn't really matter today since we don't currently do anything with Scalars
        // it's defined as nonNull(GraphQLString)
        GraphQLTypeUtil.unwrapAll(Introspection.TypeNameMetaFieldDef.getType).getName
      } else {
        // TODO: obv we need to handle nulls and return options etc.
        val fieldType =
          parentTypeName.map { parentEntityName =>
            val parentType = Option(schema.getObjectType(parentEntityName)).getOrElse(throw new NoSuchElementException(parentEntityName))
            val fieldDefn = Option(parentType.getFieldDefinition(fieldName)).getOrElse(throw new NoSuchElementException(parentEntityName + " " + fieldName))
            fieldDefn.getType
          }.getOrElse {
            schema.getQueryType.getFieldDefinition(fieldName).getType
          }
        GraphQLTypeUtil.unwrapAll(fieldType).getName
      }
    }
  }
}

private[graphql] class ContextExtractingExecutionStrategy extends ExecutionStrategy {
  override def execute(context: ExecutionContext, fieldSubSelection: FieldSubSelection): CompletableFuture[RootExecutionResultNode] = {
    // This is a hack to extract the FieldSubSelection for the query without
    // truly executing it.
    // In the future we can add a method alongside GraphQL.execute() that just returns
    // the ExecutionData directly
    // See call hierarchy of DataFetchingFieldSelectionSetImpl.newCollector()
    import scala.compat.java8.FutureConverters.FutureOps
    val result =
      new LeafExecutionResultNode(
        FetchedValueAnalysis.newFetchedValueAnalysis()
          .fetchedValue(FetchedValue.newFetchedValue()
            .fetchedValue((context, fieldSubSelection))
            .build())
          .completedValue((context, fieldSubSelection))
          .valueType(FetchedValueAnalysis.FetchedValueType.SCALAR)
          .executionStepInfo(
            ExecutionStepInfo.newExecutionStepInfo().`type`(Scalars.GraphQLString)
              .field(MergedField.newMergedField(JField.newField().build()).build())
              .build())
          .errors(new java.util.ArrayList)
          .build(),
        null
      )
    val list = new java.util.ArrayList[ExecutionResultNode]()
    list.add(result)
    Future.successful[RootExecutionResultNode](new RootExecutionResultNode(list))
      .toJava.toCompletableFuture
  }
}
