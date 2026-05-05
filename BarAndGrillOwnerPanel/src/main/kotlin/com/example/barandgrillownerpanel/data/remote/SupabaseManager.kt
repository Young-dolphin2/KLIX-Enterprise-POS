package com.example.barandgrillownerpanel.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.storage.Storage

object SupabaseManager {
    /**
     * Configuration precedence:
     * 1) JVM system properties: -Dsupabase.url / -Dsupabase.key
     * 2) Environment variables: SUPABASE_URL / SUPABASE_ANON_KEY (or SUPABASE_KEY)
     * 3) Fallback defaults (useful for local dev)
     */
    private val supabaseUrl: String by lazy {
        firstNonBlank(
            System.getProperty("supabase.url"),
            System.getenv("SUPABASE_URL"),
            "https://lwmbdrlogcordhubxbgy.supabase.co"
        )
    }

    private val supabaseKey: String by lazy {
        firstNonBlank(
            System.getProperty("supabase.key"),
            System.getenv("SUPABASE_ANON_KEY"),
            System.getenv("SUPABASE_KEY"),
            "sb_publishable_OAKmRF3LaW8dZYI-QUMkHA_eTbtlSqH"
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
    }

    fun getUrl(): String = supabaseUrl
    fun getAnonKey(): String = supabaseKey

    private fun firstNonBlank(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() }
            ?: error("Supabase configuration missing. Set -Dsupabase.url/-Dsupabase.key or SUPABASE_URL/SUPABASE_ANON_KEY.")
}
