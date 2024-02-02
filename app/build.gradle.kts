import org.jetbrains.kotlin.ir.backend.js.compile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
//    id("org.openapi.generator")
}

//task<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("azureStorage") {
//    generatorName.set("kotlin")
//    inputSpec.set("$rootDir/azure-rest-api-specs/specification/storage/resource-manager/Microsoft.Storage/stable/2023-01-01/storage.json")
//    remoteInputSpec.set("https://raw.githubusercontent.com/Azure/azure-rest-api-specs/main/specification/storage/resource-manager/Microsoft.Storage/stable/2023-01-01/blob.json")
//    outputDir.set("$buildDir/generated")
//}

//openApiGenerate {
//}

android {
    namespace = "win.hile.captureandupload"
    compileSdk = 25

    defaultConfig {
        applicationId = "win.hile.captureandupload"
        minSdk = 10
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 10
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

//    implementation("androidx.core:core-ktx:1.10.1")
//    implementation("androidx.appcompat:appcompat:1.6.1")
//    implementation("com.google.android.material:material:1.9.0")
    //noinspection GradleCompatible
    implementation("com.android.support:support-v4:25.4.0")
    implementation("com.android.support:appcompat-v7:25.4.0")
// SDK 15 以降じゃないとダメ？
//    implementation("com.microsoft.azure.android:azure-storage-android:2.0.0@aar")
    implementation("com.android.volley:volley:1.2.1")
//    implementation("com.microsoft.azure:azure-client-runtime:1.7.14")
// SDK 15 以降から
//    implementation("com.jakewharton.threetenabp:threetenabp:1.0.3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// secrets-gradle-plugin settings
secrets {
    propertiesFileName = "secrets.properties"
}
