package dev.matejgroombridge.readinglist.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The app's single [HttpClient]. Building one costs a connection pool and a
 * dispatcher, so it's shared process-wide rather than constructed per call
 * site — see the family guide's networking section.
 */
object HttpClientProvider {

    /**
     * Lenient on purpose. Open Library is a crowd-maintained catalogue whose
     * records are wildly inconsistent: fields appear and disappear per work,
     * and new ones get added without notice. `ignoreUnknownKeys` plus
     * all-nullable DTOs mean a surprise field can never turn a good search
     * into an error dialog.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            expectSuccess = true
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                // Open Library can be slow under load, but a search that
                // hasn't landed in 15s is better surfaced as a retryable
                // error than left spinning.
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
        }
    }
}
