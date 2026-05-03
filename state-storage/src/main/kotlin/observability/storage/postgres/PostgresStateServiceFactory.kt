package observability.storage.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

object PostgresStateServiceFactory {
    fun fromEnv(): PostgresStateService {
        val url = requireEnv("STATE_STORAGE_JDBC_URL")
        val user = requireEnv("STATE_STORAGE_JDBC_USER")
        val password = requireEnv("STATE_STORAGE_JDBC_PASSWORD")
        val ds = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = url
                username = user
                this.password = password
                maximumPoolSize = (System.getenv("STATE_STORAGE_POOL_SIZE")?.toIntOrNull() ?: 8)
                poolName = "state-storage"
            },
        )
        bootstrapStateStorageSchema(ds)
        return PostgresStateService(ds)
    }

    private fun requireEnv(name: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: error("environment variable $name is required to start PostgresStateService")
}
