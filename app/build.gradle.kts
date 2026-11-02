import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * 版本号为什么取自 git 提交数。
 *
 * 覆盖安装有两个硬条件：**包名相同 + 签名相同**，且 `versionCode` 不能比已装的低。
 * 用固定的 `versionCode = 1` 时，只要签名一变就得卸载重装；而每次构建都手动改数字必然忘。
 * git 提交数是单调递增且「本地构建」与「CI 构建」天然一致的（CI 需 fetch-depth: 0），
 * 所以新包永远高于旧包，可以一路覆盖升级。
 */
val gitCommitCount: Int = runCatching {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
    }.standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)

/**
 * 签名密钥的读取顺序：环境变量 → local.properties → ~/.linkplay/。
 *
 * 为什么不提交 keystore 到仓库：这是公开仓库，密钥进 Git 等于把「用同一身份签名任意 APK」的
 * 能力发给所有人。CI 通过 GitHub Secrets 注入，本地通过上面三处之一提供，key 不出机器。
 * 三者都没有时回退到 Android 默认 debug 签名 —— 别人 clone 下来照样能构建，只是那份包
 * 与本机已装版本无法互相覆盖，属于预期行为。
 */
val signingProps = Properties().apply {
    val local = rootProject.file("local.properties")
    if (local.exists()) local.inputStream().use { load(it) }
}

fun secret(env: String, prop: String, fallback: String? = null): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() }
        ?: signingProps.getProperty(prop)?.takeIf { it.isNotBlank() }
        ?: fallback

// 变量名刻意带 ks 前缀：`create("fixed") { storePassword = storePassword }` 里右侧会解析成
// 接收者自己的属性（此时还是 null），踩过一次「missing required property storePassword」。
val ksStoreFile: String? = secret("LINKPLAY_KEYSTORE", "linkplay.keystore")
    ?: System.getProperty("user.home")?.let { "$it/.linkplay/linkplay-release.jks" }
        ?.takeIf { File(it).exists() }
val ksStorePass: String? = secret("LINKPLAY_STORE_PASSWORD", "linkplay.storePassword")
val ksAlias: String = secret("LINKPLAY_KEY_ALIAS", "linkplay.keyAlias", "linkplay")!!
val ksKeyPass: String? = secret("LINKPLAY_KEY_PASSWORD", "linkplay.keyPassword", ksStorePass)

val hasFixedKeystore = ksStoreFile != null && File(ksStoreFile).exists() && ksStorePass != null

android {
    namespace = "io.github.laozhudu.linkplay.console"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.laozhudu.linkplay.console"
        minSdk = 24
        targetSdk = 35
        // +1 让首个包从 2 开始：早期 0.7.0/0.8.0 用的是 versionCode=1 且随机签名的包，
        // 卸载一次之后，后续任何新包都应被视为「更新版本」。
        versionCode = gitCommitCount
        versionName = "0.9.$gitCommitCount"
    }

    signingConfigs {
        if (hasFixedKeystore) {
            create("fixed") {
                storeFile = File(ksStoreFile!!)
                storePassword = ksStorePass
                keyAlias = ksAlias
                keyPassword = ksKeyPass
            }
        }
    }

    buildTypes {
        // debug 与 release 都用同一把钥匙：交付走的是 debug 产物，
        // 若两者签名不同，用户装过 release 后就再也覆盖不了 debug，反之亦然。
        getByName("debug") {
            if (hasFixedKeystore) signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            if (hasFixedKeystore) signingConfig = signingConfigs.getByName("fixed")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
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
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

if (!hasFixedKeystore) {
    logger.warn(
        "[linkplay] 未找到固定签名密钥，本次产物使用 Android 默认 debug 签名：" +
            "无法覆盖安装到由其他密钥签名的同一应用上。见 docs/11-install-and-upgrade.md"
    )
}

dependencies {
    implementation(project(":core-protocol"))
    implementation(project(":core-discovery"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
}
