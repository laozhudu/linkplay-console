plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

group = "io.github.laozhudu.linkplay"
version = "0.5.0"

android {
    namespace = "io.github.laozhudu.linkplay.discovery"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core-protocol"))
    implementation(libs.kotlinx.coroutines.core)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                artifactId = "linkplay-discovery"
                from(components["release"])
                pom {
                    name.set("LinkPlay Discovery")
                    description.set("SSDP discovery for LinkPlay/WiiMu devices (Android library).")
                    url.set("https://github.com/laozhudu/linkplay-console")
                }
            }
        }
        repositories {
            maven { name = "local"; url = uri(layout.buildDirectory.dir("repo")) }
        }
    }
}
