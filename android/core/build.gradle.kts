import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    api(libs.kotlinx.datetime)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}

tasks.test {
    useJUnitPlatform()
    // Golden fixtures and shared assets live at the repo root, shared with the iOS tests.
    systemProperty("zerobite.testdata", rootDir.resolve("../testdata").canonicalPath)
    systemProperty("zerobite.sharedAssets", rootDir.resolve("../shared-assets").canonicalPath)
    // The paths above are plain strings to Gradle: declare the contents so fixture edits rerun the tests.
    inputs.dir(rootDir.resolve("../testdata")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootDir.resolve("../shared-assets")).withPathSensitivity(PathSensitivity.RELATIVE)
}

// :core becomes a Kotlin Multiplatform module later, so keep JVM- and Android-only APIs out.
val checkPortable = tasks.register("checkPortable") {
    val sources = fileTree("src/main/kotlin")
    inputs.files(sources)
    doLast {
        val banned = listOf("import java.", "import javax.", "import android.")
        val offenders = sources.files.filter { file -> file.readLines().any { line -> banned.any(line::startsWith) } }
        check(offenders.isEmpty()) { "JVM/Android-only imports in :core: ${offenders.joinToString { it.name }}" }
    }
}
tasks.named("check") { dependsOn(checkPortable) }
