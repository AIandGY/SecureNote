plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.aigy.securenote"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aigy.securenote"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // 定义编译时间常量
        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")

        // 增加 Room 架构导出路径配置
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
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

    // 自定义 APK 文件名
    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val buildType = variant.buildType.name
            val versionName = variant.versionName
            output.outputFileName = "SecureNote-${buildType}-${versionName}.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.recyclerview)
    implementation(project(":core"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)

    // Gson
    implementation(libs.gson)

    // OkHttp 4
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    
    // Google Play Services Location
    implementation(libs.play.services.location)

    // Biometric
    implementation(libs.biometric)

    implementation("org.jmdns:jmdns:3.5.5")
}
