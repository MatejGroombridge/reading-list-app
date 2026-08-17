package dev.matejgroombridge.readinglist.data.network

import dev.matejgroombridge.readinglist.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
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

            // Open Library asks API consumers to identify themselves, and
            // generic or absent agents are the first thing they throttle.
            install(UserAgent) {
                agent = "ReadingList/${BuildConfig.VERSION_NAME} " +
                    "(Android; https://github.com/MatejGroombridge/reading-list-app)"
            }

            /*
             * Open Library is a free, donation-funded service and it is
             * genuinely flaky — it intermittently serves nginx 503 pages for
             * minutes at a time while other endpoints stay healthy. Without
             * retries a single unlucky 503 surfaces to the user as "search is
             * broken", which is what happened on the first release.
             *
             * Only server errors and transport failures are retried. A 4xx is
             * our own bad request and would fail identically every time.
             */
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = MAX_RETRIES)
                retryOnException(maxRetries = MAX_RETRIES, retryOnTimeout = true)
                exponentialDelay(base = 2.0, maxDelayMs = 4_000)
            }

            install(HttpTimeout) {
                // Per attempt, not per search — the retry plugin wraps this.
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
        }
    }

    /**
     * Deliberately small. Each retry is backed off, so a higher count would
     * leave the user watching a spinner for the better part of a minute when
     * Open Library is having a bad day — better to fail and offer Retry.
     */
    private const val MAX_RETRIES = 3
}
