package com.harsh.desicrics

/**
 * Everything environment-specific, read from env vars so the same build runs locally and in the cloud.
 *
 * The defaults match the Postgres started by `docker compose up -d` at the repo root. In the cloud, set
 * DATABASE_URL, DATABASE_USER and DATABASE_PASSWORD.
 */
data class ServerConfig(
    val port: Int = 8080,
    val firebaseProjectId: String = "desicrics-45117",
    val database: DatabaseConfig = DatabaseConfig(),
    /** Path to a Firebase service account key, for push messages. Without one, notifications only go to the inbox. */
    val firebaseServiceAccount: String? = null,
    /**
     * SHA-256 fingerprints of the app's signing certificates, for Android App Links: the server publishes
     * them at /.well-known/assetlinks.json so join and match links open the app without asking.
     */
    val androidCertFingerprints: List<String> = emptyList(),
    /** Origins the web app is served from, e.g. `https://desicrics.app`, allowed to call the API from a browser. */
    val webOrigins: List<String> = emptyList(),
    /** How long one second of an online auction's clock lasts. Only tests change it. */
    val auctionSecondMillis: Long = 1000,
    /** How often to look for tournaments whose entry closes within a day. Only tests change it. */
    val entryReminderEveryMillis: Long = 15 * 60 * 1000L,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()) = ServerConfig(
            port = env["PORT"]?.toIntOrNull() ?: 8080,
            firebaseProjectId = env["FIREBASE_PROJECT_ID"] ?: "desicrics-45117",
            firebaseServiceAccount = env["FIREBASE_SERVICE_ACCOUNT"]?.takeIf { it.isNotBlank() },
            androidCertFingerprints = env["ANDROID_SHA256_FINGERPRINTS"].orEmpty().csv(),
            webOrigins = env["WEB_ORIGINS"].orEmpty().csv(),
            database = DatabaseConfig(
                url = env["DATABASE_URL"] ?: DatabaseConfig.LOCAL_URL,
                user = env["DATABASE_USER"] ?: DatabaseConfig.LOCAL_USER,
                password = env["DATABASE_PASSWORD"] ?: DatabaseConfig.LOCAL_PASSWORD,
                maxPoolSize = env["DATABASE_POOL_SIZE"]?.toIntOrNull() ?: DatabaseConfig.DEFAULT_POOL_SIZE,
            ),
        )
    }
}

private fun String.csv(): List<String> = split(',').map { it.trim() }.filter { it.isNotEmpty() }

data class DatabaseConfig(
    val url: String = LOCAL_URL,
    val user: String = LOCAL_USER,
    val password: String = LOCAL_PASSWORD,
    val maxPoolSize: Int = DEFAULT_POOL_SIZE,
) {
    companion object {
        /** The local Postgres from `docker-compose.yml`. */
        const val LOCAL_URL = "jdbc:postgresql://localhost:5432/desicrics"
        const val LOCAL_USER = "desicrics"
        const val LOCAL_PASSWORD = "desicrics"
        const val DEFAULT_POOL_SIZE = 10
    }
}
