plugins {
    id("java-library")
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    `maven-publish`
    signing
}

group = "kz.oqulab"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
    jvmToolchain(11)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])

            pom {
                name.set("Raptor Language")
                description.set("Lightweight embeddable programming language interpreter written in Kotlin")
                url.set("https://github.com/oqulab/raptor-lang")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("oqulab")
                        name.set("Rauan Satanbek")
                        email.set("hello@oqulab.kz")
                    }
                }
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
            name = "sonatypeCentral"
            url = uri("https://central.sonatype.com/api/v1/publisher/deployments")
            credentials {
                username = project.findProperty("sonarCentralUsername") as String? ?: ""
                password = project.findProperty("sonarCentralPassword") as String? ?: ""
            }
        }
    }
}

signing {
    val signingKey = project.findProperty("signing.key") as String?
    val signingPassphrase = project.findProperty("signing.passphrase") as String?

    // Если свойства заполнены, настраиваем ин-мемори подпись
    if (!signingKey.isNullOrEmpty() && !signingPassphrase.isNullOrEmpty()) {
        useInMemoryPgpKeys(signingKey, signingPassphrase)
    }

    sign(publishing.publications["release"])
}