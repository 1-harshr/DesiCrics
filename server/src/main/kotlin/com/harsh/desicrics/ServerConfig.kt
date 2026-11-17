package com.harsh.desicrics

/**
 * Everything environment-specific, read from env vars so the same build runs locally and in the cloud.
 *
 * To move off the in-process H2 database, point DATABASE_URL somewhere else, e.g.
 * `jdbc:postgresql://<host>:5432/desicrics` (Cloud SQL works too). No code changes are needed.
 */
data class ServerConfig(
    val port: Int = 8080,
    val firebaseProjectId: String = "desicrics-d5fbe",
    val database: DatabaseConfig = DatabaseConfig(),
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()) = ServerConfig(
            port = env["PORT"]?.toIntOrNull() ?: 8080,
            firebaseProjectId = env["FIREBASE_PROJECT_ID"] ?: "desicrics-d5fbe",
            database = DatabaseConfig(
                url = env["DATABASE_URL"] ?: DatabaseConfig.LOCAL_H2_URL,
                user = env["DATABASE_USER"] ?: "",
                password = env["DATABASE_PASSWORD"] ?: "",
            ),
        )
    }
}

data class DatabaseConfig(
    val url: String = LOCAL_H2_URL,
    val user: String = "",
    val password: String = "",
) {
    companion object {
        /**
         * H2 running inside the server process, persisted to `server/data/`. PostgreSQL mode keeps its SQL
         * behaviour close to Postgres, which is where this is likely to move.
         */
        const val LOCAL_H2_URL = "jdbc:h2:file:./data/desicrics;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
    }
}
