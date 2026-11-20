/*
 * Copyright 2020-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.clarity.codeartifact

import com.github.dockerjava.api.exception.NotFoundException
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.profiles.ProfileFile
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.codeartifact.CodeartifactClient
import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.HexFormat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.outputStream
import kotlin.io.path.writeText

private const val EMULATOR_PORT = 9297
private const val EMULATOR_FILES = "/home/emulator/.aws/emulator/$EMULATOR_PORT"
private const val TRUST_STORE_PASSWORD = "changeit"
private const val IMAGE_NAME = "codeartifact-gradle-plugin-aws-emulator"
private const val DOCKERFILE = "aws-emulator/Dockerfile"

// AWS releases the emulator several times a day: an image older than this is rebuilt to pick up the current release
private val IMAGE_MAX_AGE: Duration = Duration.ofDays(1)

private class EmulatorContainer(image: Future<String>) : GenericContainer<EmulatorContainer>(image)

/**
 * The AWS Emulator container shared by every integration test of the JVM. It starts on first use and Testcontainers
 * removes it when the JVM exits.
 *
 * The emulator generates a new key pair and a new TLS certificate each time it starts, and rejects requests signed with
 * any other keys. Both are copied out of the container: the key pair into an AWS shared credentials file holding the
 * profiles the tests use, the certificate into a trust store.
 *
 * Every CodeArtifact client this JVM builds is sent to the emulator, so a test can never reach AWS by mistake.
 */
internal object AwsEmulator {

  /** The only AWS account the emulator acts as. */
  const val ACCOUNT_ID = "000000000000"

  /** The domain the emulator holds from the start. */
  const val DOMAIN = "my-domain"

  /** A profile holding the emulator key pair, also stored as the `default` profile. */
  const val PROFILE = "emulator"

  /** A profile holding keys the emulator does not accept. */
  const val REJECTED_PROFILE = "rejected"

  /** Keys the emulator does not accept. */
  val rejectedCredentials: AwsBasicCredentials = AwsBasicCredentials.create("AKIAIOSFODNN7EXAMPLE", "rejected-secret-access-key")

  /** The key pair this run of the emulator accepts. */
  val credentials: AwsBasicCredentials

  /**
   * The endpoint of the emulator. It uses a host name: for an IP address with a port other than the one it listens on,
   * the emulator cannot tell which service a request is for, and Docker publishes it on a random port.
   */
  val endpointUrl: String

  /** The `gradle.properties` entry that makes the daemon of a TestKit build trust the emulator certificate. */
  val daemonTrustStoreProperty: String

  /** The emulator version, its release date and the commit it was built from. */
  val version: String

  private val sharedCredentialsFile: Path
  private val configFile: Path

  init {
    val container = EmulatorContainer(emulatorImage())
      .withCommand("-p", "$EMULATOR_PORT", "--telemetry", "off", "--idle-timeout", "600")
      .withExposedPorts(EMULATOR_PORT)
      .waitingFor(Wait.forLogMessage(".*emulator server information.*\\n", 1))
    container.start()

    endpointUrl = "https://${container.host}:${container.getMappedPort(EMULATOR_PORT)}"
    version = container.execInContainer("aws-emulator", "-v").stdout.trim().replace("\n", ", ")
    credentials = container.copyFileFromContainer("$EMULATOR_FILES/emulator_credentials") { emulatorKeyPair(it.readBytes()) }

    val files = Files.createTempDirectory("aws-emulator")
    files.toFile().deleteOnExit()

    val trustStore = files.resolve("truststore.p12").also { it.toFile().deleteOnExit() }
    container.copyFileFromContainer("$EMULATOR_FILES/ca.pem") { writeTrustStore(it.readBytes(), trustStore) }
    daemonTrustStoreProperty = "org.gradle.jvmargs=" +
      "-Djavax.net.ssl.trustStore=${trustStore.invariantSeparatorsPathString} " +
      "-Djavax.net.ssl.trustStorePassword=$TRUST_STORE_PASSWORD"

    sharedCredentialsFile = files.resolve("credentials").also { it.toFile().deleteOnExit() }
    sharedCredentialsFile.writeText(
      profile("default", credentials) + profile(PROFILE, credentials) + profile(REJECTED_PROFILE, rejectedCredentials)
    )
    // Keeps the developer's own ~/.aws/config out of the tests
    configFile = files.resolve("config").also { it.toFile().deleteOnExit() }
    configFile.writeText("")

    System.setProperty("javax.net.ssl.trustStore", trustStore.toString())
    System.setProperty("javax.net.ssl.trustStorePassword", TRUST_STORE_PASSWORD)
    System.setProperty("aws.endpointUrlCodeartifact", endpointUrl)
    System.setProperty("aws.sharedCredentialsFile", sharedCredentialsFile.toString())
    System.setProperty("aws.configFile", configFile.toString())
    System.setProperty("aws.disableEc2Metadata", "true")

    createDomain()
    println("AWS Emulator ($version) listening on $endpointUrl")
  }

  /**
   * A repository url of [domain], owned by the emulator account. The emulator keeps one set of resources for every
   * region, so the region of the url does not matter.
   */
  fun repositoryUrl(domain: String = DOMAIN): String =
    "https://$domain-$ACCOUNT_ID.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

  /**
   * The environment of a TestKit build that reaches the emulator: the test environment, without any AWS or CodeArtifact
   * setting of the developer, plus [entries].
   */
  fun buildEnvironment(vararg entries: Pair<String, String>): Map<String, String> =
    System.getenv().filterKeys { !it.startsWith("AWS_") && !it.startsWith("CODEARTIFACT_") } +
      mapOf(
        "AWS_ENDPOINT_URL_CODEARTIFACT" to endpointUrl,
        "AWS_SHARED_CREDENTIALS_FILE" to sharedCredentialsFile.toString(),
        "AWS_CONFIG_FILE" to configFile.toString(),
        "AWS_EC2_METADATA_DISABLED" to "true"
      ) + entries

  private fun createDomain() {
    CodeartifactClient.builder()
      .region(Region.US_WEST_2)
      .endpointOverride(URI.create(endpointUrl))
      .credentialsProvider(StaticCredentialsProvider.create(credentials))
      .build()
      .use { client -> client.createDomain { it.domain(DOMAIN) } }
  }

  /**
   * The emulator image, reused while it is younger than [IMAGE_MAX_AGE]: a build downloads the whole emulator package
   * again even when Docker holds every layer. The tag is a digest of the Dockerfile and the architecture, so changing
   * either builds a new image.
   */
  private fun emulatorImage(): Future<String> {
    val architecture = emulatorArchitecture()
    val dockerfile = checkNotNull(AwsEmulator::class.java.classLoader.getResource(DOCKERFILE)) { "$DOCKERFILE is missing" }
    val digest = MessageDigest.getInstance("SHA-256").digest(dockerfile.readBytes() + architecture.toByteArray())
    val image = "$IMAGE_NAME:${HexFormat.of().formatHex(digest).take(12)}"

    val docker = DockerClientFactory.instance().client()
    val builtAt = try {
      OffsetDateTime.parse(docker.inspectImageCmd(image).exec().created).toInstant()
    } catch (notFound: NotFoundException) {
      null
    }
    if (builtAt != null && builtAt.isAfter(Instant.now().minus(IMAGE_MAX_AGE))) {
      println("Reusing the AWS Emulator image $image, built at $builtAt")
      return CompletableFuture.completedFuture(image)
    }

    // Rebuilding under the same tag would leave the stale image behind, 1.4 GB each time. A container still using it keeps it.
    if (builtAt != null) {
      runCatching { docker.removeImageCmd(image).exec() }
    }
    println("Building the AWS Emulator image $image")
    return ImageFromDockerfile(image, false)
      .withFileFromClasspath("Dockerfile", DOCKERFILE)
      .withBuildArg("EMULATOR_ARCH", architecture)
  }

  private fun emulatorArchitecture(): String = when (val arch = System.getProperty("os.arch")) {
    "amd64", "x86_64" -> "x86_64"
    "aarch64", "arm64" -> "aarch64"
    else -> error("AWS Emulator publishes no package for the $arch architecture")
  }

  private fun emulatorKeyPair(content: ByteArray): AwsBasicCredentials {
    val profile = ProfileFile.builder()
      .content(ByteArrayInputStream(content))
      .type(ProfileFile.Type.CREDENTIALS)
      .build()
      .profile("default")
      .orElseThrow()

    return AwsBasicCredentials.create(
      profile.property("aws_access_key_id").orElseThrow(),
      profile.property("aws_secret_access_key").orElseThrow()
    )
  }

  /**
   * Writes the JDK's trusted certificates plus the emulator one, so that the JVM reaching the emulator can still reach any
   * other HTTPS server.
   */
  private fun writeTrustStore(emulatorCertificate: ByteArray, target: Path) {
    val jdkTrustStore = Path.of(System.getProperty("java.home"), "lib", "security", "cacerts").toFile()
    val trustStore = KeyStore.getInstance(jdkTrustStore, TRUST_STORE_PASSWORD.toCharArray())
    val certificate = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(emulatorCertificate))
    trustStore.setCertificateEntry("aws-emulator", certificate)

    val pkcs12 = KeyStore.getInstance("PKCS12")
    pkcs12.load(null, null)
    trustStore.aliases().asSequence().forEach { pkcs12.setCertificateEntry(it, trustStore.getCertificate(it)) }
    target.outputStream().use { pkcs12.store(it, TRUST_STORE_PASSWORD.toCharArray()) }
  }

  private fun profile(name: String, keyPair: AwsBasicCredentials) = """
    |[$name]
    |aws_access_key_id = ${keyPair.accessKeyId()}
    |aws_secret_access_key = ${keyPair.secretAccessKey()}
    |
    |""".trimMargin()
}
