import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

// Firebase (Analytics + Crashlytics) needs app/google-services.json, which is gitignored because the
// repo is public. Without it the app still builds and runs, just without Firebase.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}

// Release signing comes from the gitignored android/keystore.properties. For Firebase App
// Distribution it names the key 1.0 was signed with, so testers get updates in place. Without it,
// as on CI, the release build is left unsigned.
val keystoreProperties = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

android {
    namespace = "com.mazesys.zerobite"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mazesys.zerobite"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            // Bundled ML Kit ships ~17 MB of native code per ABI. Phones are ARM; x86 is only
            // emulators and a few Chromebooks, so leave it out of the single release APK.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
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

/** Copies the release APK or AAB to build/outputs/release/ as zero-bite-<x.y.z>.<extension>. */
abstract class NameReleaseFile : DefaultTask() {
    @get:InputFiles abstract val source: ConfigurableFileCollection
    @get:Input abstract val fileName: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val extension = fileName.get().substringAfterLast('.')
        val built = source.asFileTree.matching { include("*.$extension") }.singleFile
        built.copyTo(outputDir.get().asFile.resolve(fileName.get()), overwrite = true)
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copySharedAssets, CopySharedAssets::outputDir)
    }
    onVariants(selector().withBuildType("release")) { variant ->
        // versionName "1.1" (matching iOS) becomes "1.1.0" in the file name.
        val version = variant.outputs.single().versionName.map { name ->
            (name.split('.') + listOf("0", "0")).take(3).joinToString(".")
        }
        val outputDir = layout.buildDirectory.dir("outputs/release")
        val apk = tasks.register<NameReleaseFile>("nameReleaseApk") {
            source.from(variant.artifacts.get(SingleArtifact.APK))
            fileName.set(version.map { "zero-bite-$it.apk" })
            this.outputDir.set(outputDir)
        }
        val aab = tasks.register<NameReleaseFile>("nameReleaseBundle") {
            source.from(variant.artifacts.get(SingleArtifact.BUNDLE))
            fileName.set(version.map { "zero-bite-$it.aab" })
            this.outputDir.set(outputDir)
        }
        tasks.configureEach {
            if (name == "assembleRelease") finalizedBy(apk)
            if (name == "bundleRelease") finalizedBy(aab)
        }
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
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.ktor.client.okhttp)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    // Analytics pulls in an old Fragment that breaks registerForActivityResult; pin a current one.
    implementation(libs.fragment)
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
