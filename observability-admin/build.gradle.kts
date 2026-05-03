import java.io.File

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "org.example"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)
    implementation(libs.hikari)
    implementation(libs.postgres.jdbc)

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("observability.admin.MainKt")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

val skipWebBuild = providers.gradleProperty("skipWebBuild").isPresent
val webDir = layout.projectDirectory.dir("web")
val staticOut = layout.projectDirectory.dir("src/main/resources/static")

val webDirFile: java.io.File = webDir.asFile
val pkgJsonFile: java.io.File = webDir.file("package.json").asFile
val staticOutFile: java.io.File = staticOut.asFile

val webEnabled = !skipWebBuild && pkgJsonFile.exists()

val npmInstall by tasks.registering(Exec::class) {
    group = "web"
    description = "Install web dependencies via npm ci"
    enabled = webEnabled
    workingDir = webDirFile
    commandLine("npm", "ci")
    inputs.file(webDir.file("package.json"))
    inputs.file(webDir.file("package-lock.json"))
    outputs.dir(webDir.dir("node_modules"))
}

val npmBuild by tasks.registering(Exec::class) {
    group = "web"
    description = "Build the SPA with Vite"
    enabled = webEnabled
    dependsOn(npmInstall)
    workingDir = webDirFile
    commandLine("npm", "run", "build")
    inputs.dir(webDir.dir("src"))
    inputs.file(webDir.file("index.html"))
    inputs.file(webDir.file("vite.config.ts"))
    inputs.file(webDir.file("package.json"))
    outputs.dir(webDir.dir("dist"))
}

val copyWebDist by tasks.registering(Sync::class) {
    group = "web"
    description = "Copy Vite build output into static resources"
    enabled = webEnabled
    dependsOn(npmBuild)
    from(webDir.dir("dist"))
    into(staticOut)
}

tasks.processResources {
    if (webEnabled) dependsOn(copyWebDist)
}

val staticOutPath = staticOutFile.absolutePath
tasks.named("clean") {
    doLast {
        File(staticOutPath).deleteRecursively()
    }
}
