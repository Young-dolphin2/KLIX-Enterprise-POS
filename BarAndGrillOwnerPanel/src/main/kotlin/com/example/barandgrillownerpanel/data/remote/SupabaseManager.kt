package com.example.barandgrillownerpanel.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp

object SupabaseManager {
    /**
     * Configuration precedence:
     * 1) JVM system properties: -Dsupabase.url / -Dsupabase.key
     * 2) Environment variables: SUPABASE_URL / SUPABASE_ANON_KEY (or SUPABASE_KEY)
     *
     * No embedded defaults are allowed; the app will fail fast if values are missing.
     */
    private val supabaseUrl: String by lazy {
        firstNonBlank(
            System.getProperty("supabase.url"),
            System.getenv("SUPABASE_URL")
        )
    }

    private val supabaseKey: String by lazy {
        firstNonBlank(
            System.getProperty("supabase.key"),
            System.getenv("SUPABASE_ANON_KEY"),
            System.getenv("SUPABASE_KEY")
        )
    }

    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseKey
    ) {
        install(Postgrest)
        install(Realtime)
        install(Auth)
        install(Storage)

        // Custom configured OkHttp engine instance to set connection pools and concurrency
        httpEngine = OkHttp.create {
            config {
                dispatcher(okhttp3.Dispatcher().apply {
                    maxRequests = 100
                    maxRequestsPerHost = 100
                })
                connectionPool(okhttp3.ConnectionPool(
                    10,
                    5,
                    java.util.concurrent.TimeUnit.MINUTES
                ))
            }
        }
    }

    fun getUrl(): String = supabaseUrl
    fun getAnonKey(): String = supabaseKey

    private fun firstNonBlank(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() }
            ?: error("Supabase configuration missing. Set -Dsupabase.url/-Dsupabase.key or SUPABASE_URL/SUPABASE_ANON_KEY.")
}
