package observability.admin.auth

object Users {
    private val credentials = mapOf(
        "admin" to "admin",
    )

    fun verify(username: String, password: String): Boolean =
        credentials[username] == password
}
