package observability.std.importer.stat

data class StatType<T>(
    val type: Class<T>,
    val statTypeId: String,
)
