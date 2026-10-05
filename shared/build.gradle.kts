import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// Hosted project for real-number testing and release (#15), from the gitignored local.properties; blank = local stack.
val hostedConfig = tasks.register("hostedConfig") {
    val local = Properties()
    rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use(local::load)
    val values = listOf("supabaseUrl", "publishableKey", "emergencyUrl", "revenuecatKey").associateWith { local.getProperty("kinfolk.$it", "").trim().trimEnd('/') }
    require(values.values.none { v -> v.any { it in "\"\\$" } }) { "kinfolk.* in local.properties can't contain \", \\ or $" }
    val out = layout.buildDirectory.dir("generated/hosted")
    inputs.properties(values)
    outputs.dir(out)
    doLast {
        out.get().file("Hosted.kt").asFile.apply { parentFile.mkdirs() }.writeText(
            values.entries.joinToString("\n", "package id.kinfolk.data\n\n", "\n") { (k, v) ->
                "internal const val HOSTED_${k.replace(Regex("([A-Z])"), "_$1").uppercase()} = \"$v\""
            }
        )
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    android {
       namespace = "id.kinfolk.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.revenuecat)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonMain {
            kotlin.srcDir(hostedConfig)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.functions)
            implementation(libs.supabase.storage)
            implementation(libs.kotlinx.datetime)
            implementation(libs.qrose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}