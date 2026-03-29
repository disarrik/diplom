package observability.common

import observability.common.model.DataIncident
import observability.common.model.StorageEntity

interface StateService {
    // идет от текущей ноды рекурсивно по родитеельским, при переходе к родительской ноде запоминает ts перехода
    //  у родительской ноды собирает инциденты, но только те что по времени до ts на переходе
    fun getActiveIncidentsRecursively(
        storageEntity: StorageEntity,
    ): List<DataIncident>
    fun getChildrenRecursively(
        storageEntity: StorageEntity
    ): List<StorageEntity>
    // returns new affected
    fun link(source: StorageEntity, target: StorageEntity)
    // returns deleted affected
    fun unlink(source: StorageEntity, target: StorageEntity): List<StorageEntity>
    // returns affected
    fun registerChange(storageEntity: StorageEntity, changeType: String): List<StorageEntity>
    // returns not affected anymore
    fun unregisterChange(storageEntity: StorageEntity, changeType: String): List<StorageEntity>
}