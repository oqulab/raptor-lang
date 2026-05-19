plugins {
    id("java-library")
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    `maven-publish`
}

group = "kz.oqulab"
version = "1.0.0"

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])
            pom {
                name.set("Raptor Language")
                description.set("Lightweight embeddable programming language interpreter written in Kotlin")
                url.set("https://github.com/oqulab/raptor-lang")
                licenses { license { name.set("MIT"); url.set("https://opensource.org/licenses/MIT") } }
                developers { developer { id.set("oqulab"); name.set("Rauan Satanbek"); email.set("hello@oqulab.kz") } }
                scm {
                    connection.set("scm:git:git://github.com/oqulab/raptor-lang.git")
                    developerConnection.set("scm:git:ssh://github.com/oqulab/raptor-lang.git")
                    url.set("https://github.com/oqulab/raptor-lang")
                }
            }
        }
    }
    repositories {
        maven {
            name = "sonatype"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = project.findProperty("SONATYPE_USERNAME") as String? ?: ""
                password = project.findProperty("SONATYPE_PASSWORD") as String? ?: ""
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
