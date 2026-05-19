# Publishing to Maven Central

## Prerequisites

1. Create a [Sonatype Jira account](https://issues.sonatype.org)
2. Claim namespace `kz.oqulab` (requires verified domain ownership of `oqulab.kz`)
3. Generate GPG key for signing artifacts

## One-time Setup

### GPG Key

```bash
gpg --gen-key
gpg --list-keys
gpg --export-secret-keys -o ~/.gnupg/secring.gpg
```

### Gradle Properties

Add to `~/.gradle/gradle.properties`:

```properties
SONATYPE_USERNAME=your_jira_username
SONATYPE_PASSWORD=your_jira_password

signing.keyId=last8chars_of_key
signing.password=your_gpg_passphrase
signing.secretKeyRingFile=/home/you/.gnupg/secring.gpg
```

## Publish

```bash
# Publish to Sonatype staging
./gradlew :raptor:publish

# Close and release staging repository via Sonatype web UI
# https://s01.oss.sonatype.org/

# Or automate via Gradle plugins (recommended for CI):
# - com.vanniktech.maven.publish
# - io.github.gradle-nexus.publish-plugin
```

## Verify

After release (sync takes ~10-30 min):

```kotlin
// build.gradle.kts of consumer project
repositories { mavenCentral() }
dependencies { implementation("kz.oqulab:raptor-lang:1.0.0") }
```

Search on: https://search.maven.org/artifact/kz.oqulab/raptor-lang
