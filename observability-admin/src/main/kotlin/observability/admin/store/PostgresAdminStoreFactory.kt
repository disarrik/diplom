package observability.admin.store

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

object PostgresAdminStoreFactory {
    fun fromEnv(): PostgresAdminStore {
        val url = requireEnv("ADMIN_DB_JDBC_URL")
        val user = requireEnv("ADMIN_DB_JDBC_USER")
        val password = requireEnv("ADMIN_DB_JDBC_PASSWORD")
        val ds = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = url
                username = user
                this.password = password
                maximumPoolSize = (System.getenv("ADMIN_DB_POOL_SIZE")?.toIntOrNull() ?: 8)
                poolName = "observability-admin"
            },
        )
        runAdminMigrations(ds)
        return PostgresAdminStore(ds)
    }

    private fun requireEnv(name: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: error("environment variable $name is required to start PostgresAdminStore")
}
