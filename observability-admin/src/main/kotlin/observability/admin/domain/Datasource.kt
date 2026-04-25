package observability.admin.domain

import kotlinx.serialization.Serializable

@Serializable
data class Datasource(
    val id: String,
    val namespace: String,
    val name: String,
    val type: String,
    val host: String,
    val teamIds: List<String>,
) {
    val fqName: String get() = "$namespace.$name"
}
