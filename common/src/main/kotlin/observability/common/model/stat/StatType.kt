package observability.common.model.stat

data class StatType<T>(
    val type: Class<T>,
    val statTypeId: String,
)
