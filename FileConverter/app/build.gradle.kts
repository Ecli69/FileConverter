plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.fileconverter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.fileconverter"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "/META-INF/{NOTICE,LICENSE,DEPENDENCIES}*"

            excludes += "license/**"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // FFmpeg
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7")
    implementation("com.arthenica:smart-exception-java:0.2.1")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // PDFBox и POI для PDF -> DOCX
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    implementation("org.apache.logging.log4j:log4j-api:2.20.0")

    configurations.all {
        exclude(group = "org.slf4j", module = "slf4j-api")
        exclude(group = "org.slf4j", module = "slf4j-simple")
        exclude(group = "org.slf4j", module = "jcl-over-slf4j")
        exclude(group = "commons-logging", module = "commons-logging")
    }

    debugImplementation(libs.androidx.ui.tooling)
}