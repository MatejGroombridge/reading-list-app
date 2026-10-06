package dev.matejgroombridge.readinglist.data.network

import dev.matejgroombridge.readinglist.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The app's one [HttpClient]. Built lazily so users with online lookup
 * switched off never pay for OkHttp's thread pools.
 */
object HttpClientProvider {

    val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 8_000
            }
            defaultRequest {
                // Open Library asks API users to identify themselves.
                header(HttpHeaders.UserAgent, "ReadingList/${BuildConfig.VERSION_NAME} (personal Android app)")
            }
        }
    }
}
