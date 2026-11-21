package com.mazesys.zerobite.core

import kotlinx.serialization.json.Json
import java.io.File

/** Reads the golden fixtures in /testdata and data in /shared-assets (paths set by Gradle). */
object Fixtures {
    val json = Json { ignoreUnknownKeys = true }

    private fun dir(property: String): File =
        File(System.getProperty(property) ?: error("$property is not set — run tests through Gradle"))

    fun text(name: String): String = File(dir("zerobite.testdata"), name).readText()

    fun sharedAsset(name: String): String = File(dir("zerobite.sharedAssets"), name).readText()

    inline fun <reified T> load(name: String): T = json.decodeFromString(text(name))
}
