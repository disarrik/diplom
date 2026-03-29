package observability.common.model

sealed interface StorageEntity {
    val namespace: String
    val name: String
}

data class FieldStorageEntity(
    override val namespace: String,
    override val name: String,
    val field: String,
): StorageEntity

data class TableStorageEntity(
    override val namespace: String,
    override val name: String,
): StorageEntity
