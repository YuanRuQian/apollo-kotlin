package com.apollographql.apollo3

import com.apollographql.apollo3.annotations.ApolloDeprecatedSince
import com.apollographql.apollo3.annotations.ApolloExperimental
import com.apollographql.apollo3.api.ApolloRequest
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.ExecutionContext
import com.apollographql.apollo3.api.MutableExecutionOptions
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.api.http.HttpHeader
import com.apollographql.apollo3.api.http.HttpMethod
import com.apollographql.apollo3.exception.ApolloException
import com.apollographql.apollo3.exception.DefaultApolloException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList

/**
 * An [ApolloCall] is a thin class that binds an [ApolloRequest] with its [ApolloClient].
 *
 * - call [ApolloCall.execute] for simple request/response cases.
 * - call [ApolloCall.toFlow] for other cases that may return more than one [ApolloResponse]. For an example
 * subscriptions, `@defer` queries, cache queries, etc...
 */
class ApolloCall<D : Operation.Data> internal constructor(
    internal val apolloClient: ApolloClient,
    val operation: Operation<D>,
) : MutableExecutionOptions<ApolloCall<D>> {
  override var executionContext: ExecutionContext = ExecutionContext.Empty
    private set
  override var httpMethod: HttpMethod? = null
    private set
  override var sendApqExtensions: Boolean? = null
    private set
  override var sendDocument: Boolean? = null
    private set
  override var enableAutoPersistedQueries: Boolean? = null
    private set
  override var canBeBatched: Boolean? = null
    private set
  @ApolloExperimental
  var retryOnError: Boolean? = null
    private set
  @ApolloExperimental
  var failFastIfOffline: Boolean? = null
    private set

  /**
   * The HTTP headers to be sent with the request.
   * By default, these are *added* on top of any HTTP header previously set on [ApolloClient]. Call [ignoreApolloClientHttpHeaders]`(true)`
   * to instead *ignore* the ones set on [ApolloClient].
   */
  override var httpHeaders: List<HttpHeader>? = null
    private set

  var ignoreApolloClientHttpHeaders: Boolean? = null
    private set

  fun failFastIfOffline(failFastIfOffline: Boolean?) = apply {
    this.failFastIfOffline = failFastIfOffline
  }

  override fun addExecutionContext(executionContext: ExecutionContext) = apply {
    this.executionContext = this.executionContext + executionContext
  }

  override fun httpMethod(httpMethod: HttpMethod?) = apply {
    this.httpMethod = httpMethod
  }

  /**
   * Sets the HTTP headers to be sent with the request.
   * By default, these are *added* on top of any HTTP header previously set on [ApolloClient]. Call [ignoreApolloClientHttpHeaders]`(true)`
   * to instead *ignore* the ones set on [ApolloClient].
   */
  override fun httpHeaders(httpHeaders: List<HttpHeader>?) = apply {
    this.httpHeaders = httpHeaders
  }

  /**
   * Add an HTTP header to be sent with the request.
   * By default, these are *added* on top of any HTTP header previously set on [ApolloClient]. Call [ignoreApolloClientHttpHeaders]`(true)`
   * to instead *ignore* the ones set on [ApolloClient].
   */
  override fun addHttpHeader(name: String, value: String) = apply {
    this.httpHeaders = (this.httpHeaders ?: emptyList()) + HttpHeader(name, value)
  }

  override fun sendApqExtensions(sendApqExtensions: Boolean?) = apply {
    this.sendApqExtensions = sendApqExtensions
  }

  override fun sendDocument(sendDocument: Boolean?) = apply {
    this.sendDocument = sendDocument
  }

  override fun enableAutoPersistedQueries(enableAutoPersistedQueries: Boolean?) = apply {
    this.enableAutoPersistedQueries = enableAutoPersistedQueries
  }

  override fun canBeBatched(canBeBatched: Boolean?) = apply {
    this.canBeBatched = canBeBatched
  }

  @ApolloExperimental
  fun retryOnError(retryOnError: Boolean?): ApolloCall<D> = apply {
    this.retryOnError = retryOnError
  }
  /**
   * If set to true, the HTTP headers set on [ApolloClient] will not be used for the call, only the ones set on this [ApolloCall] will be
   * used. If set to false, both sets of headers will be concatenated and used.
   *
   * Default: false
   */
  fun ignoreApolloClientHttpHeaders(ignoreApolloClientHttpHeaders: Boolean?) = apply {
    this.ignoreApolloClientHttpHeaders = ignoreApolloClientHttpHeaders
  }

  fun copy(): ApolloCall<D> {
    return ApolloCall(apolloClient, operation)
        .addExecutionContext(executionContext)
        .httpMethod(httpMethod)
        .httpHeaders(httpHeaders)
        .ignoreApolloClientHttpHeaders(ignoreApolloClientHttpHeaders)
        .sendApqExtensions(sendApqExtensions)
        .sendDocument(sendDocument)
        .enableAutoPersistedQueries(enableAutoPersistedQueries)
        .canBeBatched(canBeBatched)
        .retryOnError(retryOnError)
        .failFastIfOffline(failFastIfOffline)
  }

  /**
   * Returns a cold Flow that produces [ApolloResponse]s from this [ApolloCall].
   * Note that the execution happens when collecting the Flow.
   * This method can be called several times to execute a call again.
   *
   * The returned [Flow] does not throw unless [ApolloClient.useV3ExceptionHandling] is set to true.
   *
   * Example:
   * ```
   * apolloClient.subscription(NewOrders())
   *                  .toFlow()
   *                  .collect {
   *                    println("order received: ${it.data?.order?.id"})
   *                  }
   * ```
   */
  fun toFlow(): Flow<ApolloResponse<D>> {
    return apolloClient.executeAsFlow(toApolloRequest(), ignoreApolloClientHttpHeaders = ignoreApolloClientHttpHeaders == true, false)
  }

  /**
   * A version of [execute] that restores 3.x behaviour:
   * - throw on fetch errors.
   * - make `CacheFirst`, `NetworkFirst` and `CacheAndNetwork` policies ignore fetch errors.
   * - throw ApolloComposite exception if needed.
   */
  @Deprecated("Use toFlow() and handle ApolloResponse.exception instead")
  @ApolloDeprecatedSince(ApolloDeprecatedSince.Version.v4_0_0)
  fun toFlowV3(): Flow<ApolloResponse<D>> {
    @Suppress("DEPRECATION")
    return conflateFetchPolicyInterceptorResponses(true)
        .apolloClient
        .executeAsFlow(toApolloRequest(), ignoreApolloClientHttpHeaders = ignoreApolloClientHttpHeaders == true, true)
  }

  private fun toApolloRequest(): ApolloRequest<D> {
    return ApolloRequest.Builder(operation)
        .executionContext(executionContext)
        .httpMethod(httpMethod)
        .httpHeaders(httpHeaders)
        .sendApqExtensions(sendApqExtensions)
        .sendDocument(sendDocument)
        .enableAutoPersistedQueries(enableAutoPersistedQueries)
        .canBeBatched(canBeBatched)
        .retryOnError(retryOnError)
        .failFastIfOffline(failFastIfOffline)
        .build()
  }

  /**
   * A version of [execute] that restores 3.x behaviour:
   * - throw on fetch errors.
   * - make `CacheFirst`, `NetworkFirst` and `CacheAndNetwork` policies ignore fetch errors.
   * - throw ApolloComposite exception if needed.
   */
  @Deprecated("Use execute() and handle ApolloResponse.exception instead")
  @ApolloDeprecatedSince(ApolloDeprecatedSince.Version.v4_0_0)
  suspend fun executeV3(): ApolloResponse<D> {
    @Suppress("DEPRECATION")
    return singleSuccessOrException(toFlowV3())
  }

  /**
   * Retrieve a single [ApolloResponse] from this [ApolloCall], ignoring any cache misses or network errors.
   *
   * Use this for queries and mutations to get a single value from the network or the cache.
   * For subscriptions or operations using `@defer`, you usually want to use [toFlow] instead to listen to all values.
   *
   * @throws ApolloException if the call returns zero or multiple valid GraphQL responses.
   */
  suspend fun execute(): ApolloResponse<D> {
    return singleSuccessOrException(toFlow())
  }

  private suspend fun singleSuccessOrException(flow: Flow<ApolloResponse<D>>): ApolloResponse<D> {
    val responses = flow.toList()
    val (exceptionResponses, successResponses) = responses.partition { it.exception != null }
    return when (successResponses.size) {
      0 -> {
        when (exceptionResponses.size) {
          0 -> throw DefaultApolloException("The operation did not emit any item, check your interceptor chain")
          1 -> exceptionResponses.first()
          else -> {
            val first = exceptionResponses.first()
            first.newBuilder()
                .exception(
                    exceptionResponses.drop(1).fold(first.exception!!) { acc, response ->
                      acc.also {
                        it.addSuppressed(response.exception!!)
                      }
                    }
                )
                .build()
          }
        }
      }

      1 -> successResponses.first()
      else -> throw DefaultApolloException("The operation returned multiple items, use .toFlow() instead of .execute()")
    }
  }
}
