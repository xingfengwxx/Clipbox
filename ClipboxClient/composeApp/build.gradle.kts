import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

// 服务器地址由构建配置下发，配对页不再要求用户手输；本地验收可用 -Pclipbox.defaultServerUrl 临时覆盖
val defaultServerUrl = providers.gradleProperty("clipbox.defaultServerUrl")
    .getOrElse("http://117.72.121.6:8080")
val bundledConfigDir = layout.buildDirectory.dir("generated/bundledConfig/commonMain/kotlin")
val generateBundledConfig = tasks.register("generateBundledConfig") {
    val url = defaultServerUrl
    inputs.property("defaultServerUrl", url)
    outputs.dir(bundledConfigDir)
    doLast {
        val file = bundledConfigDir.get()
            .file("com/wangxingxing/clipbox/config/BundledConfig.kt")
            .asFile
        file.parentFile.mkdirs()
        val escapedUrl = url.replace("\\", "\\\\").replace("\"", "\\\"")
        file.writeText(
            """
            |/**
            | * author : 王星星
            | * date : 2026-11-16 17:10
            | * email : 1099420259@qq.com
            | * description : 构建期注入的默认服务器地址；由 generateBundledConfig 生成，禁止手改，本地覆盖使用 -Pclipbox.defaultServerUrl
            | */
            |package com.wangxingxing.clipbox.config
            |
            |internal object BundledConfig {
            |    const val DEFAULT_SERVER_URL: String = "$escapedUrl"
            |}
            |""".trimMargin(),
        )
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(bundledConfigDir)
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(libs.androidx.navigation.compose)
                implementation(libs.koin.core)
                implementation(project(":shared"))
            }
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

// 代码生成产物需先于所有 Kotlin 编译任务存在；按任务名匹配可同时覆盖 JVM、Android 与 Native
tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }.configureEach {
    dependsOn(generateBundledConfig)
}

android {
    namespace = "com.wangxingxing.clipbox"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    // KMP 源码集使用 androidMain 目录，AGP 默认的 src/main 需要显式指过去
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")

    defaultConfig {
        applicationId = "com.wangxingxing.clipbox"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

compose.resources {
    packageOfResClass = "com.wangxingxing.clipbox.resources"
}

compose.desktop {
    application {
        mainClass = "com.wangxingxing.clipbox.MainKt"
    }
}
