plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

// 为什么这个模块不做成零依赖（不像 core-protocol）：
// 它要 kotlinx-serialization（解析 Radio Browser 的 JSON）和 kotlinx-coroutines（suspend 网络）。
// core-protocol 是纯 JVM 零依赖底座、要 publish 出去，这两个依赖进去就不纯了。
// 所以按 docs/69 的判据 1（拖第三方依赖就开模块）单独成模块。
dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // 只要 core-protocol 里的 RadioStation 数据类（收藏模型），
    // 用来把"目录候选"转成"能存进收藏的形状"。core-protocol 本身零依赖。
    implementation(project(":core-protocol"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed", "skipped")
    }
}
