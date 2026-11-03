plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

group = "io.github.laozhudu.linkplay"
version = "0.5.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
    // 只给测试用：MediaProvider 的 search/resolve 是 suspend，单测需要 runBlocking。
    // 主代码保持零依赖（纯 JVM、无 Android），发布出去的 jar 不带协程。
    testImplementation(libs.kotlinx.coroutines.core)
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "linkplay-protocol"
            from(components["java"])
            pom {
                name.set("LinkPlay Protocol")
                description.set(
                    "LinkPlay/WiiMu device protocol client: httpapi, UPnP AVTransport/RenderingControl, " +
                        "private PlayQueue service, provisioning, local media server. Pure JVM, no Android dependencies.",
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
        // 只发布到本地 Maven 仓库：本库给个人项目和对接实验用，不做中央仓库分发
        maven { name = "local"; url = uri(layout.buildDirectory.dir("repo")) }
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed", "skipped")
    }
}
