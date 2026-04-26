package observability.admin.http.routes

import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.authRoutes() {
    get("/auth/me") {
        val principal = call.principal<UserIdPrincipal>()!!
        call.respond(mapOf("username" to principal.name))
    }
}
