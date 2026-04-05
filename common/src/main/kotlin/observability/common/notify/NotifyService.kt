package observability.common.notify

import observability.common.model.StorageEntity
import observability.common.model.DataIncident

interface NotifyService {
    fun notify(
        storageEntity: StorageEntity,
        dataIncident: DataIncident,
        finished: Boolean = false,
    )
}
