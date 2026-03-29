package observability

import observability.common.model.StorageEntity
import observability.common.model.DataIncident

class NotifyService {
    fun notify(
        storageEntity: StorageEntity,
        dataIncident: DataIncident,
        finished: Boolean = false,
    ) {
        // todo
    }
}