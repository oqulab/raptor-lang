# Publishing to Maven Central (New Central Portal)

This project is configured to publish to the [Maven Central Portal](https://central.sonatype.com/).

## Prerequisites

1.  **Verified Namespace**: Claim `kz.oqulab` on the portal (requires DNS verification).
2.  **Deployment Token**:
    *   Go to [Account Settings -> Deployment Tokens](https://central.sonatype.com/account).
    *   Generate a new token.
3.  **GPG Key**: Artifacts must be signed.
    *   Generate a key: `gpg --full-generate-key`
    *   Distribute your public key: `gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID`

## Local Setup

Add the following to your `~/.gradle/gradle.properties`:

```properties
# Sonatype Central Portal (Deployment Token)
mavenCentralUsername=YOUR_TOKEN_USERNAME
mavenCentralPassword=YOUR_TOKEN_PASSWORD

# GPG Signing
signing.keyId=YOUR_KEY_ID_LAST_8_CHARS
signing.password=YOUR_GPG_PASSPHRASE

# Private Key (for In-Memory signing, recommended)
# You can get this via: gpg --export-secret-keys --armor YOUR_KEY_ID | pbcopy
signing.secretKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\n...\n-----END PGP PRIVATE KEY BLOCK-----

# OR use a secret key ring file:
# signing.secretKeyRingFile=/Users/username/.gnupg/secring.gpg
```

## Publish

To publish the library, run:

```bash
./gradlew publishAllPublicationsToSonatypeRepository
```

After the command finishes, go to the [Central Portal Deployments](https://central.sonatype.com/publishing/deployments) to check the status and "Publish" the deployment if it's in the "Validated" state.

## Verification

After release (sync takes ~10-30 min):

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("kz.oqulab:raptor-lang:1.0.0")
}
```
