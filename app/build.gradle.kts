plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.codexbar.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.codexbar.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.0.5-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("Boolean", "NATIVE_CLI_ENABLED", "false")
        val copilotClientId = providers.environmentVariable("COPILOT_OAUTH_CLIENT_ID").getOrElse("")
        require(copilotClientId.matches(Regex("[A-Za-z0-9._-]{0,128}"))) { "Invalid public Copilot OAuth client ID" }
        buildConfigField("String", "COPILOT_CLIENT_ID", "\"$copilotClientId\"")
    }

    signingConfigs {
        create("nativeAcceptance") {
            storeFile = System.getenv("ANDROID_KEYSTORE_PATH")?.let { file(it) }
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("Boolean", "IS_DEBUG", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "IS_DEBUG", "false")
        }
        create("nativeDebug") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".native"
            signingConfig = signingConfigs.getByName("nativeAcceptance")
            matchingFallbacks += "debug"
            buildConfigField("Boolean", "NATIVE_CLI_ENABLED", "true")
        }
        create("nativeRelease") {
            initWith(getByName("release"))
            isDebuggable = false
            applicationIdSuffix = ".native"
            signingConfig = signingConfigs.getByName("nativeAcceptance")
            matchingFallbacks += "release"
            ndk.abiFilters += "arm64-v8a"
            versionNameSuffix = "-native"
            buildConfigField("Boolean", "NATIVE_CLI_ENABLED", "true")
        }
        create("nativeAcceptance") {
            initWith(getByName("nativeRelease"))
            applicationIdSuffix = ".native.acceptance"
            matchingFallbacks += "release"
        }
    }

    sourceSets.getByName("nativeDebug") {
        val nativeAbi = providers.gradleProperty("nativeAbi").getOrElse("x86_64")
        require(nativeAbi in listOf("x86_64", "arm64-v8a")) { "Unsupported nativeAbi" }
        jniLibs.srcDir(rootProject.file("build/native/package/$nativeAbi/jniLibs"))
        assets.srcDir(rootProject.file("build/native/package/$nativeAbi/assets"))
    }
    sourceSets.getByName("nativeRelease") {
        jniLibs.srcDir(rootProject.file("build/native/package/release/arm64-v8a/jniLibs"))
        assets.srcDir(rootProject.file("build/native/package/release/arm64-v8a/assets"))
    }
    sourceSets.getByName("nativeAcceptance") {
        java.srcDir("src/nativeDebug/java")
        manifest.srcFile("src/nativeDebug/AndroidManifest.xml")
        jniLibs.srcDir(rootProject.file("build/native/package/release/arm64-v8a/jniLibs"))
        assets.srcDir(rootProject.file("build/native/package/release/arm64-v8a/assets"))
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
        jniLibs.useLegacyPackaging = true
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    @Suppress("UnstableApiUsage")
    bundle {
        storeArchive {
            enable = true
        }
    }
}

tasks.matching { it.name in listOf("preNativeDebugBuild", "preNativeReleaseBuild", "preNativeAcceptanceBuild") }.configureEach {
    doFirst {
        val release = name != "preNativeDebugBuild"
        val abi = if (release) "arm64-v8a" else providers.gradleProperty("nativeAbi").getOrElse("x86_64")
        val payload = rootProject.file("build/native/package/${if (release) "release/" else ""}$abi")
        check(file("$payload/jniLibs/$abi/libcodexbar.so").isFile &&
            file("$payload/jniLibs/$abi/libc++_shared.so").isFile &&
            file("$payload/assets/codexbar/CodexBar_CodexBarCore.bundle").isDirectory) {
            "Missing native payload. Run native/build.py --tools <toolchain-root> --abi $abi --configuration ${if (release) "release" else "debug"} first."
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.startup.runtime)
    implementation(libs.datastore.preferences)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit.kotlinx.serialization)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Security
    implementation(libs.security.crypto)

    // Glance (AppWidget)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Accompanist
    implementation(libs.accompanist.permissions)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.mockito.core)
}
