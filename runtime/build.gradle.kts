plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("io.ktor:ktor-server-core:3.0.3")
                implementation("io.ktor:ktor-server-netty:3.0.3")
                implementation("io.ktor:ktor-server-websockets:3.0.3")
                implementation("io.ktor:ktor-server-content-negotiation:3.0.3")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
                implementation("io.ktor:ktor-client-core:3.0.3")
                implementation("io.ktor:ktor-client-cio:3.0.3")
                implementation("io.ktor:ktor-client-websockets:3.0.3")
                implementation("org.xerial:sqlite-jdbc:3.45.1.0")
                implementation("ch.qos.logback:logback-classic:1.4.14")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
                implementation("io.ktor:ktor-server-test-host:3.0.3")
            }
        }
    }
}
