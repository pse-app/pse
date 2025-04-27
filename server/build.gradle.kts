import java.util.Properties

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
}

dependencies {
    api(project(":common"))
    api(libs.bundles.server.auth)
    api(libs.bundles.server.db)
    api(libs.bundles.ktor.server)
    api(libs.bundles.ktor.client.backend)
    api(libs.joptsimple)

    testImplementation(libs.mockk)
    testImplementation(libs.bundles.ktor.server.test)
    testImplementation(libs.junit.jupiter)

    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = "com.pse_app.server.MainKt"
}

tasks.named<Jar>("jar") {
    manifest.attributes(
        "Specification-Title" to "Program for Splitting Expenses",
        "Specification-Version" to project.version.toString(),
        "Specification-Vendor" to "PSE Team",
        "Implementation-Title" to "PSE Server",
        "Implementation-Version" to project.version.toString(),
        "Implementation-Vendor" to "PSE Team"
    )
}

val properties = Properties()
if (project.rootProject.file("local.properties").isFile) {
    properties.load(project.rootProject.file("local.properties").inputStream())
}

tasks.named<JavaExec>("run") {
    jvmArgs("-Dterminal.ansi=true")
    jvmArgs("-Dlog4j.configurationFile=${rootProject.file("run/log4j2-dev.xml").toPath().toAbsolutePath()}")
    if (properties.getProperty("pse.db.transient")?.let(String::toBoolean) != false) {
        args("--transient")
    }
    when (val dbInitPath = properties.getProperty("pse.db.init")) {
        null -> {}
        else -> args("--database-init", rootProject.file(dbInitPath).toPath().toAbsolutePath().toString())
    }
    args(rootProject.file("run/config.properties").toPath().toAbsolutePath().toString())
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.from(project.file("detekt.yml"))
}
