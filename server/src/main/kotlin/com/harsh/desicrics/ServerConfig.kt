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
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()) = ServerConfig(
            port = env["PORT"]?.toIntOrNull() ?: 8080,
            firebaseProjectId = env["FIREBASE_PROJECT_ID"] ?: "desicrics-45117",
            database = DatabaseConfig(
                url = env["DATABASE_URL"] ?: DatabaseConfig.LOCAL_URL,
                user = env["DATABASE_USER"] ?: DatabaseConfig.LOCAL_USER,
                password = env["DATABASE_PASSWORD"] ?: DatabaseConfig.LOCAL_PASSWORD,
                maxPoolSize = env["DATABASE_POOL_SIZE"]?.toIntOrNull() ?: DatabaseConfig.DEFAULT_POOL_SIZE,
            ),
        )
    }
}

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
