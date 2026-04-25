package observability.admin.integrations

class IntegrationRegistry {
    private val providers = linkedMapOf<String, IntegrationProvider>()

    fun register(provider: IntegrationProvider) {
        providers[provider.id] = provider
    }

    fun all(): List<IntegrationProvider> = providers.values.toList()

    fun get(id: String): IntegrationProvider? = providers[id]
}
