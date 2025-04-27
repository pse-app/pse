import java.net.URI
import java.util.*

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
}

val appId = "com.pseapp.client"
val appNamespace = "com.pse_app.client"

val properties = Properties()
if (project.rootProject.file("local.properties").isFile) {
    properties.load(project.rootProject.file("local.properties").inputStream())
}
val signingEnabled = properties.getProperty("sign.store.file") != null

// NOTE: apiPathPrefix must have a leading "/"
val publicApi: URI = run {
    val configuredUri = URI.create((properties.getProperty("pse.server") ?: "http://10.0.2.2:8080") + "/").normalize()
    if (configuredUri.scheme == null) URI.create("https://${configuredUri}/").normalize() else configuredUri
}
val apiScheme = publicApi.scheme!!
val apiHost = publicApi.host!!.removePrefix("www.")
val apiPathPrefix = publicApi.path!! + "v1/join/"

android {
    namespace = appId
    compileSdk = 34

    defaultConfig {
        applicationId = appId
        minSdk = 29
        targetSdk = 34
        versionName = project.version.toString()
        versionCode = project.version.toString().split(".").fold(0) { r, s -> 1000 * r + s.toInt() }
        namespace = appNamespace
        testNamespace = "com.pse_app.client.tests"
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // set the redirect scheme so that the appauth activity in the androidmanifest knows
        // about it
        manifestPlaceholders["appAuthRedirectScheme"] = appId
        manifestPlaceholders["serverApiScheme"] = apiScheme
        manifestPlaceholders["serverApiHost"] = apiHost
        manifestPlaceholders["serverApiPathPrefix"] = apiPathPrefix
        manifestPlaceholders["isDebug"] = "false"

        // allows us to get the redirect scheme set from here at runtime using the BuildConfig class
        buildConfigField(
            "String",
            "REDIRECT_SCHEME",
            // The content of the string is directly injected as Java code in the BuildConfig class
           "\"$appId\""
        )
        
        buildConfigField(
            "String",
            "PUBLIC_API",
            // The content of the string is directly injected as Java code in the BuildConfig class
           "\"$publicApi\""
        )

        buildConfigField(
            "java.util.List<String>",
            "JOIN_API_URIS",
            """
                java.util.List.of(
                    "$apiScheme://$apiHost$apiPathPrefix",
                    "$apiScheme://www.$apiHost$apiPathPrefix"
                )
            """.trimIndent()
        )

        if (signingEnabled) {
            signingConfigs {
                register("release") {
                    keyAlias = properties.getProperty("sign.key.alias") ?: ""
                    keyPassword = properties.getProperty("sign.key.password") ?: ""
                    storeFile = rootProject.file(".").toPath().resolve(properties.getProperty("sign.store.file")!!).toFile()
                    storePassword = properties.getProperty("sign.store.password") ?: ""
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            if (signingEnabled) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            namespace = appNamespace
            isDebuggable = true
            manifestPlaceholders["isDebug"] = "true"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            testOptions.unitTests {
                excludes.add("META-INF/*")
            }
        }
    }
}

dependencies {
    implementation(project(":common"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.android.libs)
    implementation(libs.bundles.ktor.client.app)
    implementation(libs.bundles.client.auth)
    implementation(libs.kotlin.serialization)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.bundles.ktor.client.test)
    testImplementation(libs.bundles.client.test)
    testImplementation("org.json:json:20210307")

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.bundles.client.test)

    debugImplementation(libs.bundles.android.debug)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.from(project.file("detekt.yml"))
}

kover {
    reports {
       filters {
           excludes {
               annotatedBy(
                   "com.pse_app.client.annotations.KoverIgnore",
                   "androidx.compose.runtime.Composable"
               )

               // Contains UI and Android-Specific code that can't be unit-tested
               // or is already covered by instrumented tests
               packages("com.pse_app.client.ui.view")
               packages("com.pse_app.client.ui.theme")
               packages("com.pse_app.client.persistence")

               // Contains only exception classes
               packages("com.pse_app.client.model.exceptions")
               // Classes in this package are only used for previews and tests
               packages("com.pse_app.client.model.facade.stubs")
           }
       }
    }
}

afterEvaluate {
    tasks.named("connectedAndroidTest") {
        dependsOn(project(":integration").tasks.named("runAsync"))
        mustRunAfter(project(":integration").tasks.named("runAsync"))
    }
}
