import java.util.concurrent.ScheduledThreadPoolExecutor

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":server"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = "com.pse_app.integration.MainKt"
}

tasks.named<JavaExec>("run") {
    jvmArgs("-Dterminal.ansi=true")
    jvmArgs("-Dlog4j.configurationFile=${rootProject.file("run/log4j2-dev.xml").toPath().toAbsolutePath()}")
    args("--transient")
    args("--database-init", project.file("init.sql").toPath().toAbsolutePath().toString())
}

tasks.register("runAsync") {
    dependsOn(tasks.named("jar"))
    doNotTrackState("this is a run task")
    outputs.upToDateWhen { false }
    doLast {
        val run: JavaExec = tasks.named<JavaExec>("run").get()
        run.classpath.files // Eagerly resolve the configuration
        ScheduledThreadPoolExecutor(1).submit {
            try {
                project.javaexec {
                    classpath = run.classpath
                    mainClass = run.mainClass
                    jvmArgs = run.jvmArgs
                    args = run.args
                    standardOutput = System.out
                    errorOutput = System.err
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}
