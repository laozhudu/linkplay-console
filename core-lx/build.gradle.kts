plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.laozhudu.linkplay.lx"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // QuickJS 的 AAR 带四个 ABI 的 so（arm64/armv7/x86/x86_64，共约 5MB）。
    // 库模块不能自己裁 ABI —— 那是打包 APK 时决定的（在 app 的 abiFilters 里）。
    // 这里只保证 so 不被压缩，避免运行时解压失败。
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

/**
 * 为什么这个模块是 Android 模块而不是纯 JVM：
 *
 * 它要跑洛雪的自定义源脚本，靠的是 QuickJS 的 Android 封装
 * （`wang.harlon.quickjs:wrapper-android`，Apache-2.0，带 JNI + .so）。
 * core-protocol 必须保持纯 JVM 零依赖（要 publish），所以按 docs/69 判据 1
 * （拖纯 JVM 装不下的依赖就开模块）单独成模块。
 *
 * 纯逻辑部分（协议报文、加密、脚本头解析、HTTP）都写成不依赖 Android 的类，
 * 这样 `:core-lx:test` 能在 JVM 上跑；只有引擎绑定那层需要真机。
 */
dependencies {
    api(project(":core-protocol"))
    implementation(project(":core-diagnostics"))
    // 脚本仓库在 core-script（纯 JVM）；这里只提供"洛雪脚本长什么样"的校验实现。
    // 用 api 而不是 implementation：app 组装时要同时看到仓库和校验器，别让它漏依赖。
    api(project(":core-script"))

    implementation(libs.quickjs.wrapper.android)
    implementation(libs.kotlinx.coroutines.core)
    // 协议报文是动态形状（脚本自己定义），用 JsonElement 而不是 @Serializable 数据类
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed", "skipped")
    }
}
