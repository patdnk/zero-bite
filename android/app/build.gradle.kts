plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.mazesys.zerobite"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mazesys.zerobite"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Per-app language (Android 13+): English (en-GB, the unqualified resources) and Polish.
    androidResources {
        generateLocaleConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // Robolectric reaches into JDK internals that JDK 17+ hides.
                test.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
                test.systemProperty("zerobite.testdata", rootDir.resolve("../testdata").canonicalPath)
            }
        }
    }
}

kotlin {
    compilerOptions {
        optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.uuid.ExperimentalUuidApi")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

/**
 * Copies the recipe catalogue, its Polish overlay, tag names and Polish ingredient aliases from
 * /shared-assets so iOS and Android bundle the same files.
 */
abstract class CopySharedAssets : DefaultTask() {
    @get:InputFiles abstract val files: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.mkdirs()
        files.forEach { it.copyTo(out.resolve(it.name), overwrite = true) }
    }
}

val copySharedAssets = tasks.register<CopySharedAssets>("copySharedAssets") {
    val shared = rootProject.layout.projectDirectory.dir("../shared-assets")
    files.from(listOf("recipes.json", "recipes.pl.json", "recipe-tags.json", "ingredient-aliases.pl.json").map(shared::file))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copySharedAssets, CopySharedAssets::outputDir)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.core.splashscreen)
    implementation(libs.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
