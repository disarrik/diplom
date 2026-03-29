package observability.common

import observability.common.model.StorageEntity

interface StateService {
    // returns new affected
    fun link(source: StorageEntity, target: StorageEntity): List<StorageEntity>
    // returns deleted affected
    fun unlink(source: StorageEntity, target: StorageEntity): List<StorageEntity>
    // returns affected
    fun registerChange(storageEntity: StorageEntity, changeType: String): List<StorageEntity>
    // returns not affected anymore
    fun unregisterChange(storageEntity: StorageEntity, changeType: String): List<StorageEntity>
}