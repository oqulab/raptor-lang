import java.security.MessageDigest

plugins {
    id("java-library")
    kotlin("jvm") version "2.3.21"
    alias(libs.plugins.kotlin.serialization)
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
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
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
}

signing {
    val keyId = project.findProperty("signing.keyId") as String?
    val password = project.findProperty("signing.password") as String?
    val keyFile = project.findProperty("signing.secretKeyRingFile") as String?

    if (keyId != null && password != null && keyFile != null) {
        val file = file(keyFile)
        if (file.exists() && file.readText().contains("BEGIN PGP PRIVATE KEY BLOCK")) {
            useInMemoryPgpKeys(keyId, file.readText(), password)
        }
    }
    sign(publishing.publications["maven"])
}

// Задача для создания ZIP-архива для ручной загрузки в Central Portal
tasks.register<Zip>("bundleRelease") {
    dependsOn("publishMavenPublicationToMavenLocal")
    
    val groupDir = project.group.toString().replace(".", "/")
    val artifactDir = project.name
    val versionDir = project.version.toString()
    
    val repoRoot = File(System.getProperty("user.home"), ".m2/repository")
    val inputDir = File(repoRoot, "$groupDir/$artifactDir/$versionDir")
    
    // Генерируем чексуммы перед упаковкой
    doFirst {
        inputDir.listFiles()?.forEach { file ->
            if (file.isFile && !file.name.endsWith(".md5") && !file.name.endsWith(".sha1")) {
                val bytes = file.readBytes()
                
                val md5 = MessageDigest.getInstance("MD5")
                    .digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                File(file.absolutePath + ".md5").writeText(md5)
                
                val sha1 = MessageDigest.getInstance("SHA-1")
                    .digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                File(file.absolutePath + ".sha1").writeText(sha1)
            }
        }
    }
    
    // Сохраняем структуру папок в ZIP (важно для портала)
    from(inputDir) {
        into("$groupDir/$artifactDir/$versionDir")
    }
    
    archiveFileName.set("raptor-lang-1.0.0.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}
