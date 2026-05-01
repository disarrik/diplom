package observability.admin.plugins

class PluginRegistry {
    private val plugins = linkedMapOf<String, Plugin>()

    fun register(plugin: Plugin) {
        plugins[plugin.id] = plugin
    }

    fun all(): List<Plugin> = plugins.values.toList()

    fun get(id: String): Plugin? = plugins[id]
}
