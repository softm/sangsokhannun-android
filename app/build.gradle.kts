plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val productionKeys = listOf(
    "ADMOB_APP_ID", "ADMOB_NATIVE_ID", "ADMOB_BANNER_ID",
    "ADMOB_INTERSTITIAL_ID", "ADMOB_EXIT_ID"
)
fun productionValue(key: String): String = providers.gradleProperty(key)
    .orElse(providers.environmentVariable(key)).orNull?.trim().orEmpty()
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.softm.sangsokhannun"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.softm.sangsokhannun"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "WEB_URL", quoted("https://softm.github.io/sangsokhannun/"))
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Google-owned demo IDs; production builds use five separately configured IDs.
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_NATIVE_ID", quoted("ca-app-pub-3940256099942544/2247696110"))
            buildConfigField("String", "ADMOB_BANNER_ID", quoted("ca-app-pub-3940256099942544/6300978111"))
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", quoted("ca-app-pub-3940256099942544/1033173712"))
            buildConfigField("String", "ADMOB_EXIT_ID", quoted("ca-app-pub-3940256099942544/6300978111"))
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["admobAppId"] = productionValue("ADMOB_APP_ID")
            productionKeys.drop(1).forEach { key ->
                buildConfigField("String", key, quoted(productionValue(key)))
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

val validateProductionAds by tasks.registering {
    doLast {
        val appPattern = Regex("ca-app-pub-\\d{16}~\\d{10}")
        val unitPattern = Regex("ca-app-pub-\\d{16}/\\d{10}")
        productionKeys.forEach { key ->
            val value = productionValue(key)
            val valid = (if (key == "ADMOB_APP_ID") appPattern else unitPattern).matches(value)
            require(valid && !value.startsWith("ca-app-pub-3940256099942544")) {
                "Release requires a separately issued SangsokHannun " + key + ". Configure it in ~/.gradle/gradle.properties or CI secrets."
            }
        }
        require(productionKeys.drop(1).map(::productionValue).distinct().size == 4) {
            "Each production AdMob placement must have its own ad unit ID."
        }
    }
}
tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(validateProductionAds)
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.webkit:webkit:1.13.0")
    implementation("com.github.softm:hannun-admob-android:" + providers.gradleProperty("hannunAdmobVersion").get())
    implementation("com.github.softm:hannun-in-app-update-android:" + providers.gradleProperty("hannunUpdateVersion").get())
    testImplementation("junit:junit:4.13.2")
}
