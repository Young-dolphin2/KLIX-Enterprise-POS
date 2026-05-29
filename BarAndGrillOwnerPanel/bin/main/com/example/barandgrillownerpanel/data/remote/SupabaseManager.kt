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
     * Configuration precedence (nullable):
     * 1) JVM system properties: -Dsupabase.url / -Dsupabase.key
     * 2) Environment variables: SUPABASE_URL / SUPABASE_ANON_KEY (or SUPABASE_KEY)
     *
     * Initialization is tolerant: if values are missing or client creation fails,
     * `client` will be null and callers should handle that case. This prevents
     * a hard crash during class initialization when running locally without
     * environment variables (e.g., developer machines).
     */
    private fun firstNonBlankNullable(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }

    private val supabaseUrl: String? by lazy {
        firstNonBlankNullable(
            System.getProperty("supabase.url"),
            System.getenv("SUPABASE_URL")
        )
    }

    private val supabaseKey: String? by lazy {
        firstNonBlankNullable(
            System.getProperty("supabase.key"),
            System.getenv("SUPABASE_ANON_KEY"),
            System.getenv("SUPABASE_KEY")
        )
    }

    val client: SupabaseClient? by lazy {
        if (supabaseUrl.isNullOrBlank() || supabaseKey.isNullOrBlank()) {
            com.example.barandgrillownerpanel.utils.Logger.warn(
                "SUPABASE",
                "Supabase URL or Key not configured; operating in offline/no-remote mode."
            )
            return@lazy null
        }

        try {
            createSupabaseClient(
                supabaseUrl = supabaseUrl!!,
                supabaseKey = supabaseKey!!
            ) {
                install(Postgrest)
                install(Realtime)
                install(Auth)
                install(Storage)

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
        } catch (t: Throwable) {
            com.example.barandgrillownerpanel.utils.Logger.error("SUPABASE", "Failed to initialize Supabase client", t)
            null
        }
    }

    fun getUrl(): String? = supabaseUrl
    fun getAnonKey(): String? = supabaseKey
}


