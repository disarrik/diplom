package observability.admin.auth

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.basic

const val ADMIN_AUTH = "admin"

fun Application.installAuth() {
    install(Authentication) {
        basic(ADMIN_AUTH) {
            realm = "observability-admin"
            validate { creds ->
                if (Users.verify(creds.name, creds.password)) UserIdPrincipal(creds.name) else null
            }
        }
    }
}
