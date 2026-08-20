import java.io.FileInputStream
import java.util.Properties
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class GenerateBundledDefaultConfig : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val json = inputFile.get().asFile.readText()
        val root = JsonSlurper().parseText(json) as Map<*, *>
        require(root["format"] == "retro-control-data") { "Unsupported default config format" }
        require((root["version"] as Number).toInt() == 1) { "Unsupported default config version" }
        require((root["items"] as? List<*>)?.isNotEmpty() == true) {
            "Default config must contain items"
        }
        val items = root["items"] as List<*>
        val fanCurveIds = items.mapNotNull { entry ->
            (entry as? Map<*, *>)?.takeIf { it["type"] == "fan-curve" }?.get("sourceId")
                as? String
        }.toSet()
        require(fanCurveIds.containsAll(setOf("quiet", "normal", "performance"))) {
            "Default config must contain quiet, normal, and performance fan curves"
        }
        val settings = root["settings"] as? Map<*, *>
            ?: error("Default config must contain settings")
        val requiredSettings = setOf(
            "gamePresetId", "nonGamePresetId", "fanCurveId", "fanEnabled",
            "joystickEnabled", "buttonLayoutTileId", "performanceTileId",
            "overlayEnabled", "autoStartEnabled", "profileSwitchToastsEnabled",
            "usbThermalControlEnabled", "usbThermalFanCurveDefault",
            "thermalProtectionDisabled", "preserveBypassCharging",
            "chargingThreshold", "overlayX", "overlayY",
        )
        require(settings.keys.containsAll(requiredSettings)) {
            "Default config is missing settings: ${'$'}{requiredSettings - settings.keys}"
        }
        fun quoted(value: String): String = buildString {
            append('"')
            value.forEach { character ->
                append(
                    when (character) {
                        '\\' -> "\\\\"
                        '"' -> "\\\""
                        '\r' -> "\\r"
                        '\n' -> "\\n"
                        else -> character
                    }
                )
            }
            append('"')
        }
        val booleanCases = settings.entries
            .filter { it.value is Boolean }
            .joinToString("\n") { "        ${quoted(it.key as String)} -> ${it.value}" }
        val intCases = settings.entries
            .filter { it.value is Number }
            .joinToString("\n") {
                "        ${quoted(it.key as String)} -> ${(it.value as Number).toInt()}"
            }
        val stringCases = settings.entries
            .filter { it.value is String }
            .joinToString("\n") {
                "        ${quoted(it.key as String)} -> ${quoted(it.value as String)}"
            }
        val nullableStringCases = settings.entries
            .filter { it.value == null || it.value is String }
            .joinToString("\n") {
                val value = (it.value as? String)?.let(::quoted) ?: "null"
                "        ${quoted(it.key as String)} -> $value"
            }
        val fanCurveCases = items.mapNotNull { entry ->
            (entry as? Map<*, *>)?.takeIf { it["type"] == "fan-curve" }
        }.flatMap { item ->
            val sourceId = item["sourceId"] as String
            val data = item["data"] as Map<*, *>
            listOf("points", "defaultPoints").map { pointsKey ->
                val serialized = (data[pointsKey] as List<*>).joinToString(",", "percent|") {
                    val point = it as Map<*, *>
                    "${(point["temperatureC"] as Number).toInt()}:" +
                        (point["speedPercent"] as Number).toInt()
                }
                "        ${quoted("$sourceId:$pointsKey")} -> ${quoted(serialized)}"
            }
        }.joinToString("\n")
        val itemStringCases = items.mapNotNull { it as? Map<*, *> }.flatMap { item ->
            val type = item["type"] as? String ?: return@flatMap emptyList()
            val sourceId = item["sourceId"] as? String ?: return@flatMap emptyList()
            val values = buildMap<String, Any?> {
                put("name", item["name"])
                (item["data"] as? Map<*, *>)?.forEach { (key, value) ->
                    if (key is String && (value == null || value is String)) put(key, value)
                }
            }
            values.map { (key, value) ->
                val literal = (value as? String)?.let(::quoted) ?: "null"
                "        ${quoted("$type:$sourceId:$key")} -> $literal"
            }
        }.joinToString("\n")
        val output = outputDirectory.file(
            "com/mmax/retrocontrol/data/BundledDefaultConfig.kt"
        ).get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            """package com.mmax.retrocontrol.data

/** Generated from config/default/RetroControl-data.json. Do not edit. */
internal object BundledDefaultConfig {
    fun settingBoolean(key: String): Boolean = when (key) {
$booleanCases
        else -> error("Missing Boolean default setting: ${'$'}key")
    }

    fun settingInt(key: String): Int = when (key) {
$intCases
        else -> error("Missing Int default setting: ${'$'}key")
    }

    fun settingString(key: String): String = when (key) {
$stringCases
        else -> error("Missing String default setting: ${'$'}key")
    }

    fun nullableSettingString(key: String): String? = when (key) {
$nullableStringCases
        else -> error("Missing nullable String default setting: ${'$'}key")
    }

    fun fanCurveSerialized(sourceId: String, pointsKey: String): String =
        when ("${'$'}sourceId:${'$'}pointsKey") {
$fanCurveCases
            else -> error("Missing fan curve in bundled default config: ${'$'}sourceId")
        }

    fun itemString(type: String, sourceId: String, key: String): String? =
        when ("${'$'}type:${'$'}sourceId:${'$'}key") {
$itemStringCases
            else -> error("Missing default item value: ${'$'}type/${'$'}sourceId/${'$'}key")
        }
}
"""
        )
    }
}

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val bundledDefaultConfigFile = rootProject.file("config/default/RetroControl-data.json")
val generatedDefaultConfigDir = layout.buildDirectory.dir("generated/source/defaultConfig")
val generateBundledDefaultConfig = tasks.register<GenerateBundledDefaultConfig>(
    "generateBundledDefaultConfig"
) {
    inputFile.set(bundledDefaultConfigFile)
    outputDirectory.set(generatedDefaultConfigDir)
}
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.mmax.retrocontrol"
    compileSdk = 37

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.mmax.retrocontrol"
        minSdk = 26
        targetSdk = 37
        versionCode = 20035
        versionName = "2.0.0 beta 35"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.java?.addGeneratedSourceDirectory(
            generateBundledDefaultConfig,
            GenerateBundledDefaultConfig::outputDirectory,
        )
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  implementation(project(":core:designsystem"))
  implementation(project(":feature:authorization"))
  implementation(project(":feature:fan"))
  implementation(project(":feature:joystick"))

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
  implementation(libs.androidx.compose.material3.adaptive)
  implementation(libs.androidx.compose.material.icons.extended)

  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)

  // Local tests
  testImplementation(libs.junit)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)

  // Coroutines
  implementation(libs.kotlinx.coroutines.android)

  // libsu — persistent root shell
  implementation(libs.libsu.core)
}
