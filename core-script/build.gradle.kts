plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

group = "io.github.laozhudu.linkplay"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

/**
 * 为什么是纯 JVM 模块而不是 Android 模块：
 *
 * 这里管的是"用户交给我们的脚本"—— 校验、存盘、列出、删除、当前启用哪一支。
 * 全部只用 `java.io`，目录由调用方传进来（Android 传 `filesDir`，桌面传任意目录，
 * 单测传临时目录）。所以它能在 JVM 上跑全部单测，也能被非 Android 宿主直接复用。
 *
 * 这与 `core-diagnostics` 同一条判据：**能力本身通用，落哪里是宿主的事**。
 */
dependencies {
    testImplementation(libs.junit)
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "script"
            from(components["java"])
            pom {
                name.set("Script")
                description.set(
                    "Script repository for pluggable content sources: validate a pasted or picked script, " +
                        "store it, list what is installed, switch the active one. Pure JVM, zero runtime deps.",
                )
                url.set("https://github.com/laozhudu/linkplay-console")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
    repositories {
        maven { name = "local"; url = uri(layout.buildDirectory.dir("repo")) }
    }
}

tasks.withType<Test>().configureEach {
    testLogging { events("passed", "failed", "skipped") }
}
