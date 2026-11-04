plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

group = "io.github.laozhudu.linkplay"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "diagnostics"
            from(components["java"])
            pom {
                name.set("Diagnostics")
                description.set(
                    "In-app diagnostics kit: severity-tagged ring log, self-registering status sections, " +
                        "screenshot-friendly plain-text report. Pure JVM, zero runtime dependencies, no Android.",
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
