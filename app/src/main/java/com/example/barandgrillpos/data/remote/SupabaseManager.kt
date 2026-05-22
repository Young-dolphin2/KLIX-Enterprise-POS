package com.example.barandgrillpos.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.auth.Auth

object SupabaseManager {
    /**
     * Configuration precedence:
     * 1) Android build config fields (recommended for release builds)
     * 2) System properties (useful for local/debug tooling): -Dsupabase.url / -Dsupabase.key
     * 3) Environment variables: SUPABASE_URL / SUPABASE_ANON_KEY (or SUPABASE_KEY)
     *
     * No embedded defaults are allowed; the app will fail fast if values are missing.
     * IMPORTANT: Do not use the service-role key in client apps.
     */
    private val supabaseUrl: String by lazy {
        firstNonBlank(
            // If you later add BuildConfig fields, they can go first here.
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

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseKey
        ) {
            install(Postgrest)
            install(Realtime)
            install(Auth)
        }
    }

    private fun firstNonBlank(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() }
            ?: error("Supabase configuration missing. Set SUPABASE_URL and SUPABASE_ANON_KEY.")
}
