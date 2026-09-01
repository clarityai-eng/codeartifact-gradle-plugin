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

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.net.InetSocketAddress

private const val codeArtifactUrl =
  "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

private const val serviceUserAccessKeyId = "AKIAIOSFODNN7EXAMPLE"

// How the plugin is expected to redact the access key id in the build log
private const val maskedServiceUserAccessKeyId = "AKIA************MPLE"

/**
 * Stub of the CodeArtifact GetAuthorizationToken endpoint, listening on a random local port.
 */
private fun stubCodeArtifactTokenEndpoint(): HttpServer =
  HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
    createContext("/") { exchange ->
      val bytes = """{"authorizationToken":"stub-token","expiration":1893456000}""".toByteArray()
      exchange.responseHeaders.add("Content-Type", "application/json")
      exchange.sendResponseHeaders(200, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
    }
    start()
  }

/**
 * Build script declaring [repoUrl] as a plain Maven repository, so that the plugin detects it, plus a task printing the
 * credentials the plugin ended up injecting.
 */
private fun printRepoCredentialsBuild(repoUrl: String) = $$"""
  plugins {
      id("ai.clarity.codeartifact")
  }

  repositories {
      maven {
          url = uri("$$repoUrl")
      }
  }

  tasks.register("printRepoCredentials") {
      doLast {
          val repo = project.repositories.first() as org.gradle.api.artifacts.repositories.MavenArtifactRepository
          println("username=${repo.credentials.username}")
          println("password=${repo.credentials.password}")
      }
  }
  """.trimIndent()

/**
 * Functional tests for the 'ai.clarity.codeartifact' plugin using Gradle TestKit.
 *
 * These tests spin up real Gradle builds in a temporary directory and verify
 * the plugin's behavior end-to-end.
 */
class ClarityCodeartifactPluginFunctionalTest {

  // Because of the issue https://github.com/gradle/gradle/issues/34505 only versions from 8.2 upward can be tested.
  // The plugin should be compatible with all the versions from 7.3 upward (first version to support Java17)
  // Limit the number of versions to reduce time needed to test
  companion object {
    @JvmStatic
    fun gradleVersions() = listOf("8.4", "8.7", "8.14", "9.1.0", "9.4.0")
  }

  @Nested
  inner class ProjectPluginFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private val buildFile by lazy { File(projectDir, "build.gradle.kts") }
    private val settingsFile by lazy { File(projectDir, "settings.gradle.kts") }

    @BeforeEach
    fun setUp() {
      // Given: a minimal Gradle project with the plugin applied
      settingsFile.writeText("""rootProject.name = "test-project"""")
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("ai.clarity.codeartifact.ClarityCodeartifactPluginFunctionalTest#gradleVersions")
    fun `plugin applies successfully and tasks are available`(gradleVersion: String) {
      // Given: a build file that only applies the plugin
      buildFile.writeText(
        """
        plugins {
            id("ai.clarity.codeartifact")
        }
        
        tasks.register("verifyPlugin") {
            doLast {
                println("Plugin applied: " + project.plugins.hasPlugin("ai.clarity.codeartifact"))
            }
        }
        """.trimIndent()
      )

      // When: running the custom verification task
      val result = createRunner(gradleVersion)
        .withArguments("verifyPlugin")
        .build()

      // Then: the build succeeds and the plugin is confirmed applied
      assertThat(result.output).contains("Plugin applied: true")
      assertThat(result.task(":verifyPlugin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("ai.clarity.codeartifact.ClarityCodeartifactPluginFunctionalTest#gradleVersions")
    fun `plugin ignores non-codeartifact repositories`(gradleVersion: String) {
      // Given: a build file with a standard Maven Central repository
      buildFile.writeText(
        $$"""
        plugins {
            id("ai.clarity.codeartifact")
        }

        repositories {
            maven {
                url = uri("https://repo.maven.apache.org/maven2/")
            }
        }

        tasks.register("printRepoCredentials") {
            doLast {
                val repo = project.repositories.first() as org.gradle.api.artifacts.repositories.MavenArtifactRepository
                println("username=${repo.credentials.username}")
                println("password=${repo.credentials.password}")
            }
        }
        """.trimIndent()
      )

      // When: running the task that prints credentials
      val result = createRunner(gradleVersion)
        .withArguments("printRepoCredentials")
        .build()

      // Then: credentials remain null — the plugin leaves non-CodeArtifact repos untouched
      assertThat(result.output).contains("username=null")
      assertThat(result.output).contains("password=null")
      assertThat(result.task(":printRepoCredentials")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("ai.clarity.codeartifact.ClarityCodeartifactPluginFunctionalTest#gradleVersions")
    fun `plugin attempts to configure credentials for codeartifact repository URLs`(gradleVersion: String) {
      buildFile.writeText(
        $$"""
        plugins {
            id("ai.clarity.codeartifact")
        }

        repositories {
            maven {
                url = uri("$$codeArtifactUrl")
            }
        }
        
        tasks.register("resolve") {
            doLast {
                println("Repositories configured: ${project.repositories.size}")
            }
        }
        """.trimIndent()
      )

      // When: running the build (expecting a failure due to missing AWS credentials)
      val result = createRunner(gradleVersion)
        .withArguments("resolve", "--info")
        .buildAndFail()

      // Then: the failure confirms the plugin tried to fetch a CodeArtifact token
      assertThat(result.output).containsIgnoringCase("Getting token for $codeArtifactUrl in profile")
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("ai.clarity.codeartifact.ClarityCodeartifactPluginFunctionalTest#gradleVersions")
    fun `plugin configures credentials via Kotlin DSL codeartifact method`(gradleVersion: String) {
      // Given: a build file that uses the codeartifact(...) Kotlin DSL
      buildFile.writeText(
        """
        import ai.clarity.codeartifact.codeartifact

        plugins {
            id("ai.clarity.codeartifact")
        }

        repositories {
            codeartifact("$codeArtifactUrl")
        }
        
        tasks.register("verifyRepo") {
            doLast {
                val repo = project.repositories.first() as MavenArtifactRepository
                println("Repo URL: $${"{"}repo.url}")
                println("Repo credentials username: $${"{"}repo.credentials.username}")
            }
        }
        """.trimIndent()
      )

      // When: running the task (expecting it to attempt to fetch a token, even if it fails due to no AWS creds)
      val result = createRunner(gradleVersion)
        .withArguments("verifyRepo", "--info")
        .buildAndFail()

      // Then: the output confirms it tried to use the codeartifact service
      assertThat(result.output).containsIgnoringCase("Getting token for $codeArtifactUrl in profile")
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("ai.clarity.codeartifact.ClarityCodeartifactPluginFunctionalTest#gradleVersions")
    fun `plugin configures credentials via Kotlin DSL codeartifact method with action`(gradleVersion: String) {
      // Given: a build file that uses the codeartifact(...) Kotlin DSL with an action
      buildFile.writeText(
        """
        import ai.clarity.codeartifact.codeartifact

        plugins {
            id("ai.clarity.codeartifact")
        }

        repositories {
            codeartifact("$codeArtifactUrl", "default") {
                name = "customCodeArtifactRepo"
            }
        }
        
        tasks.register("verifyRepo") {
            doLast {
                val repo = project.repositories.getByName("customCodeArtifactRepo") as MavenArtifactRepository
                println("Repo name: $${"{"}repo.name}")
            }
        }
        """.trimIndent()
      )

      // When: running the task
      val result = createRunner(gradleVersion)
        .withArguments("verifyRepo", "--info")
        .buildAndFail()
      println(result.output)
      assertThat(result.output).containsIgnoringCase("Getting token for $codeArtifactUrl in profile")
    }

    private fun createRunner(gradleVersion: String): GradleRunner {
      return GradleRunner.create()
        .withGradleVersion(gradleVersion)
        .forwardOutput()
        .withPluginClasspath()
        .withProjectDir(projectDir)
    }
  }

  @Nested
  inner class SettingsPluginFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private val settingsFile by lazy { File(projectDir, "settings.gradle.kts") }
    private val buildFile by lazy { File(projectDir, "build.gradle.kts") }

    @BeforeEach
    fun setUp() {
      buildFile.writeText("")
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("ai.clarity.codeartifact.ClarityCodeartifactPluginFunctionalTest#gradleVersions")
    fun `plugin applies successfully to settings`(gradleVersion: String) {
      settingsFile.writeText(
        """
        plugins {
            id("ai.clarity.codeartifact")
        }
        rootProject.name = "test-settings-project"
        """.trimIndent()
      )

      val result = createRunner(gradleVersion)
        .withArguments("help")
        .build()

      assertThat(result.task(":help")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("ai.clarity.codeartifact.ClarityCodeartifactPluginFunctionalTest#gradleVersions")
    fun `plugin configures credentials in pluginManagement`(gradleVersion: String) {
      settingsFile.writeText(
        """
        pluginManagement {
            repositories {
                maven {
                    url = uri("$codeArtifactUrl")
                }
            }
        }
        plugins {
            id("ai.clarity.codeartifact")
        }
        
        
        rootProject.name = "test-settings-plugin-management"
        """.trimIndent()
      )

      // Expect failure because of missing AWS credentials, which proves it tried to configure
      val result = createRunner(gradleVersion)
        .withArguments("help", "--info")
        .buildAndFail()

      assertThat(result.output).containsIgnoringCase("Getting token for $codeArtifactUrl in profile")
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("ai.clarity.codeartifact.ClarityCodeartifactPluginFunctionalTest#gradleVersions")
    fun `plugin configures credentials in dependencyResolutionManagement`(gradleVersion: String) {
      settingsFile.writeText(
        """
        plugins {
            id("ai.clarity.codeartifact")
        }
        
        dependencyResolutionManagement {
            repositories {
                maven {
                    url = uri("$codeArtifactUrl")
                }
            }
        }
        
        rootProject.name = "test-settings-dependency-resolution"
        """.trimIndent()
      )

      // Expect failure because of missing AWS credentials, which proves it tried to configure
      val result = createRunner(gradleVersion)
        .withArguments("help", "--info")
        .buildAndFail()

      assertThat(result.output).containsIgnoringCase("Getting token for $codeArtifactUrl in profile")
    }

    private fun createRunner(gradleVersion: String): GradleRunner {
      return GradleRunner.create()
        .withGradleVersion(gradleVersion)
        .forwardOutput()
        .withPluginClasspath()
        .withProjectDir(projectDir)
    }
  }

  @Nested
  inner class GroovyDslFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private val buildFile by lazy { File(projectDir, "build.gradle") }
    private val settingsFile by lazy { File(projectDir, "settings.gradle") }

    @BeforeEach
    fun setUp() {
      settingsFile.writeText("rootProject.name = 'test-groovy-project'")
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("ai.clarity.codeartifact.ClarityCodeartifactPluginFunctionalTest#gradleVersions")
    fun `plugin configures credentials via Groovy DSL codeartifact method`(gradleVersion: String) {
      // Given: a Groovy build file that uses the codeartifact(...) dynamic method
      buildFile.writeText(
        """
        plugins {
            id 'ai.clarity.codeartifact'
        }

        repositories {
            codeartifact('$codeArtifactUrl')
        }
        """.trimIndent()
      )

      // When: running the build (expecting a failure due to missing AWS credentials)
      val result = createRunner(gradleVersion)
        .withArguments("help", "--info")
        .buildAndFail()

      // Then: the failure confirms the Groovy DSL method tried to fetch a CodeArtifact token
      assertThat(result.output).containsIgnoringCase("Getting token for $codeArtifactUrl in profile default")
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("ai.clarity.codeartifact.ClarityCodeartifactPluginFunctionalTest#gradleVersions")
    fun `plugin configures credentials via Groovy DSL codeartifact method with profile and closure`(gradleVersion: String) {
      // Given: a Groovy build file that uses codeartifact(...) with a profile and a configuration closure
      buildFile.writeText(
        """
        plugins {
            id 'ai.clarity.codeartifact'
        }

        repositories {
            codeartifact('$codeArtifactUrl', 'my-profile') {
                name = 'customCodeArtifactRepo'
            }
        }
        """.trimIndent()
      )

      // When: running the build (expecting a failure due to missing AWS credentials)
      val result = createRunner(gradleVersion)
        .withArguments("help", "--info")
        .buildAndFail()

      // Then: the failure confirms the requested profile reached the token fetch
      assertThat(result.output).containsIgnoringCase("Getting token for $codeArtifactUrl in profile my-profile")
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("ai.clarity.codeartifact.ClarityCodeartifactPluginFunctionalTest#gradleVersions")
    fun `plugin attempts to configure credentials for maven repositories declared in Groovy DSL`(gradleVersion: String) {
      // Given: a Groovy build file relying on automatic detection of the CodeArtifact URL
      buildFile.writeText(
        """
        plugins {
            id 'ai.clarity.codeartifact'
        }

        repositories {
            maven {
                url = uri('$codeArtifactUrl')
            }
        }
        """.trimIndent()
      )

      // When: running the build (expecting a failure due to missing AWS credentials)
      val result = createRunner(gradleVersion)
        .withArguments("help", "--info")
        .buildAndFail()

      // Then: the failure confirms the plugin detected the repository and tried to fetch a token
      assertThat(result.output).containsIgnoringCase("Getting token for $codeArtifactUrl in profile")
    }

    private fun createRunner(gradleVersion: String): GradleRunner {
      return GradleRunner.create()
        .withGradleVersion(gradleVersion)
        .forwardOutput()
        .withPluginClasspath()
        .withProjectDir(projectDir)
    }
  }

  @Nested
  inner class ProfileResolutionFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private val buildFile by lazy { File(projectDir, "build.gradle.kts") }
    private val settingsFile by lazy { File(projectDir, "settings.gradle.kts") }
    private val gradlePropertiesFile by lazy { File(projectDir, "gradle.properties") }

    @BeforeEach
    fun setUp() {
      settingsFile.writeText("""rootProject.name = "test-profile-resolution"""")
      buildFile.writeText(
        """
        plugins {
            id("ai.clarity.codeartifact")
        }

        repositories {
            maven {
                url = uri("$codeArtifactUrl")
            }
        }
        """.trimIndent()
      )
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("ai.clarity.codeartifact.ClarityCodeartifactPluginFunctionalTest#gradleVersions")
    fun `profile default from gradle properties is used`(gradleVersion: String) {
      // Given: a project-wide default profile committed in gradle.properties
      gradlePropertiesFile.writeText("systemProp.codeartifact.profile=dev")

      // When: running the build (expecting a failure due to missing AWS credentials)
      val result = createRunner(gradleVersion)
        .withArguments("help", "--info")
        .buildAndFail()

      // Then: the token fetch uses the project default profile
      assertThat(result.output).containsIgnoringCase("Getting token for $codeArtifactUrl in profile dev")
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("ai.clarity.codeartifact.ClarityCodeartifactPluginFunctionalTest#gradleVersions")
    fun `profile default from a plain gradle property is used`(gradleVersion: String) {
      // Given: the default profile written without the systemProp. prefix, the way a Gradle plugin is usually configured
      gradlePropertiesFile.writeText("codeartifact.profile=dev")

      // When: running the build (expecting a failure due to missing AWS credentials)
      val result = createRunner(gradleVersion)
        .withArguments("help", "--info")
        .buildAndFail()

      // Then: the token fetch uses the project default profile
      assertThat(result.output).containsIgnoringCase("Getting token for $codeArtifactUrl in profile dev")
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("ai.clarity.codeartifact.ClarityCodeartifactPluginFunctionalTest#gradleVersions")
    fun `command line project property overrides the plain gradle properties default`(gradleVersion: String) {
      // Given: a project-wide default profile written plainly
      gradlePropertiesFile.writeText("codeartifact.profile=dev")

      // When: running the build with -P, as a CI pipeline would
      val result = createRunner(gradleVersion)
        .withArguments("help", "--info", "-Pcodeartifact.profile=ci")
        .buildAndFail()

      // Then: the command line wins over the gradle.properties default
      assertThat(result.output).containsIgnoringCase("Getting token for $codeArtifactUrl in profile ci")
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("ai.clarity.codeartifact.ClarityCodeartifactPluginFunctionalTest#gradleVersions")
    fun `a plain gradle property shadows the system property and says so`(gradleVersion: String) {
      // Given: the same setting configured both ways, which is how a half-finished migration to the plain form looks
      gradlePropertiesFile.writeText("codeartifact.profile=dev")

      // When: the command line tries to override it with -D, which no longer reaches the plugin
      val result = createRunner(gradleVersion)
        .withArguments("help", "--info", "-Dcodeartifact.profile=ci")
        .buildAndFail()

      // Then: the Gradle property wins, and the build warns that the -D override is being ignored
      assertThat(result.output).containsIgnoringCase("Getting token for $codeArtifactUrl in profile dev")
      assertThat(result.output).contains(
        "The codeartifact.profile Gradle property takes precedence over the codeartifact.profile system property"
      )
      assertThat(result.output).contains("Override the Gradle property with -Pcodeartifact.profile")
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("ai.clarity.codeartifact.ClarityCodeartifactPluginFunctionalTest#gradleVersions")
    fun `a plain gradle property shadows the environment variable and says so`(gradleVersion: String) {
      // Given: a plain default in gradle.properties and a CODEARTIFACT_PROFILE that used to win before the plain form was read
      gradlePropertiesFile.writeText("codeartifact.profile=dev")

      // When: running the build with the environment variable pointing elsewhere
      val result = createRunner(gradleVersion)
        .withEnvironment(System.getenv() + ("CODEARTIFACT_PROFILE" to "ci"))
        .withArguments("help", "--info")
        .buildAndFail()

      // Then: the Gradle property wins, and the build warns that the environment variable is being ignored
      assertThat(result.output).containsIgnoringCase("Getting token for $codeArtifactUrl in profile dev")
      assertThat(result.output).contains(
        "The codeartifact.profile Gradle property takes precedence over the CODEARTIFACT_PROFILE environment variable"
      )
      assertThat(result.output).contains("Override the Gradle property with -Pcodeartifact.profile")
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("ai.clarity.codeartifact.ClarityCodeartifactPluginFunctionalTest#gradleVersions")
    fun `command line system property overrides the gradle properties default`(gradleVersion: String) {
      // Given: a project-wide default profile and a CI-style command line override
      gradlePropertiesFile.writeText("systemProp.codeartifact.profile=dev")

      // When: running the build with -D, as a CI pipeline would
      val result = createRunner(gradleVersion)
        .withArguments("help", "--info", "-Dcodeartifact.profile=ci")
        .buildAndFail()

      // Then: the command line wins over the gradle.properties default
      assertThat(result.output).containsIgnoringCase("Getting token for $codeArtifactUrl in profile ci")
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("ai.clarity.codeartifact.ClarityCodeartifactPluginFunctionalTest#gradleVersions")
    fun `url query param keeps precedence over the gradle properties default`(gradleVersion: String) {
      // Given: a repository url with an explicit profile and a different project default
      gradlePropertiesFile.writeText("systemProp.codeartifact.profile=dev")
      buildFile.writeText(
        """
        plugins {
            id("ai.clarity.codeartifact")
        }

        repositories {
            maven {
                url = uri("$codeArtifactUrl?profile=url-profile")
            }
        }
        """.trimIndent()
      )

      // When: running the build (expecting a failure due to missing AWS credentials)
      val result = createRunner(gradleVersion)
        .withArguments("help", "--info")
        .buildAndFail()

      // Then: the url query param wins over the gradle.properties default
      assertThat(result.output).containsIgnoringCase("in profile url-profile")
    }

    private fun createRunner(gradleVersion: String): GradleRunner {
      return GradleRunner.create()
        .withGradleVersion(gradleVersion)
        .forwardOutput()
        .withPluginClasspath()
        .withProjectDir(projectDir)
    }
  }

  @Nested
  inner class TokenEndpointFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private val buildFile by lazy { File(projectDir, "build.gradle.kts") }
    private val settingsFile by lazy { File(projectDir, "settings.gradle.kts") }

    private lateinit var server: HttpServer

    @BeforeEach
    fun setUp() {
      settingsFile.writeText("""rootProject.name = "test-token-endpoint"""")
      server = stubCodeArtifactTokenEndpoint()
    }

    @AfterEach
    fun tearDown() {
      server.stop(0)
    }

    @Test
    fun `plugin fetches the token from the codeartifact endpoint and configures credentials`() {
      // Given: a build file relying on automatic detection of the CodeArtifact URL
      buildFile.writeText(printRepoCredentialsBuild(codeArtifactUrl))

      // The AWS SDK resolves the service endpoint and static credentials from these variables,
      // so the token request hits the local stub instead of AWS
      val environment = System.getenv()
        .minus(listOf("AWS_PROFILE", "CODEARTIFACT_PROFILE", "AWS_SESSION_TOKEN")) +
        mapOf(
          "AWS_ENDPOINT_URL_CODEARTIFACT" to "http://127.0.0.1:${server.address.port}",
          "AWS_ACCESS_KEY_ID" to "test-access-key",
          "AWS_SECRET_ACCESS_KEY" to "test-secret-key",
        )

      // When: running a build with the environment pointing to the stub
      val result = GradleRunner.create()
        .forwardOutput()
        .withPluginClasspath()
        .withProjectDir(projectDir)
        .withEnvironment(environment)
        .withArguments("printRepoCredentials")
        .build()

      // Then: the repository ends up configured with the stubbed token
      assertThat(result.output).contains("username=aws")
      assertThat(result.output).contains("password=stub-token")
      assertThat(result.task(":printRepoCredentials")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }
  }

  @Nested
  inner class ServiceCredentialsFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private val buildFile by lazy { File(projectDir, "build.gradle.kts") }
    private val groovyBuildFile by lazy { File(projectDir, "build.gradle") }
    private val settingsFile by lazy { File(projectDir, "settings.gradle.kts") }
    private val gradlePropertiesFile by lazy { File(projectDir, "gradle.properties") }

    private lateinit var server: HttpServer

    @BeforeEach
    fun setUp() {
      settingsFile.writeText("""rootProject.name = "test-service-credentials"""")
      server = stubCodeArtifactTokenEndpoint()
    }

    @AfterEach
    fun tearDown() {
      server.stop(0)
    }

    @Test
    fun `service credentials from the environment are used to fetch the token`() {
      // Given: a repository detected automatically, with no AWS credentials available to the SDK
      buildFile.writeText(printRepoCredentialsBuild(codeArtifactUrl))

      // When: only the CodeArtifact specific variables carry the service account credentials
      val result = runnerWithoutAwsCredentials()
        .withEnvironment(
          environmentWithoutAwsCredentials(
            "AWS_ENDPOINT_URL_CODEARTIFACT" to "http://127.0.0.1:${server.address.port}",
            "CODEARTIFACT_ACCESS_KEY_ID" to serviceUserAccessKeyId,
            "CODEARTIFACT_SECRET_ACCESS_KEY" to "service-user-secret"
          )
        )
        .withArguments("printRepoCredentials", "--info")
        .build()

      // Then: the token is fetched with the service credentials and the access key id is masked in the log
      assertThat(result.output).contains("with the service credentials $maskedServiceUserAccessKeyId")
      assertThat(result.output).doesNotContain("service-user-secret")
      assertThat(result.output).contains("username=aws")
      assertThat(result.output).contains("password=stub-token")
      assertThat(result.task(":printRepoCredentials")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `service credentials from gradle properties are used to fetch the token`() {
      // Given: the service account credentials declared as system properties of the build
      buildFile.writeText(printRepoCredentialsBuild(codeArtifactUrl))
      gradlePropertiesFile.writeText(
        """
        systemProp.codeartifact.accessKeyId=$serviceUserAccessKeyId
        systemProp.codeartifact.secretAccessKey=service-user-secret
        """.trimIndent()
      )

      // When: running the build with no AWS credentials in the environment
      val result = runnerWithoutAwsCredentials()
        .withEnvironment(
          environmentWithoutAwsCredentials("AWS_ENDPOINT_URL_CODEARTIFACT" to "http://127.0.0.1:${server.address.port}")
        )
        .withArguments("printRepoCredentials", "--info")
        .build()

      // Then: the repository ends up configured with the token issued for the service account
      assertThat(result.output).contains("with the service credentials $maskedServiceUserAccessKeyId")
      assertThat(result.output).contains("password=stub-token")
    }

    @Test
    fun `service credentials from plain gradle properties are used to fetch the token`() {
      // Given: the service account credentials declared without the systemProp. prefix
      buildFile.writeText(printRepoCredentialsBuild(codeArtifactUrl))
      gradlePropertiesFile.writeText(
        """
        codeartifact.accessKeyId=$serviceUserAccessKeyId
        codeartifact.secretAccessKey=service-user-secret
        """.trimIndent()
      )

      // When: running the build with no AWS credentials in the environment
      val result = runnerWithoutAwsCredentials()
        .withEnvironment(
          environmentWithoutAwsCredentials("AWS_ENDPOINT_URL_CODEARTIFACT" to "http://127.0.0.1:${server.address.port}")
        )
        .withArguments("printRepoCredentials", "--info")
        .build()

      // Then: the repository ends up configured with the token issued for the service account
      assertThat(result.output).contains("with the service credentials $maskedServiceUserAccessKeyId")
      assertThat(result.output).doesNotContain("service-user-secret")
      assertThat(result.output).contains("password=stub-token")
    }

    @Test
    fun `a repository profile keeps precedence over the service credentials of the build`() {
      // Given: service credentials for the whole build and an explicit profile on the repository
      buildFile.writeText(printRepoCredentialsBuild("$codeArtifactUrl?profile=url-profile"))
      gradlePropertiesFile.writeText(
        """
        systemProp.codeartifact.accessKeyId=$serviceUserAccessKeyId
        systemProp.codeartifact.secretAccessKey=service-user-secret
        """.trimIndent()
      )

      // When: running the build (expecting a failure because the profile does not exist)
      val result = runnerWithoutAwsCredentials()
        .withEnvironment(
          environmentWithoutAwsCredentials("AWS_ENDPOINT_URL_CODEARTIFACT" to "http://127.0.0.1:${server.address.port}")
        )
        .withArguments("printRepoCredentials", "--info")
        .buildAndFail()

      // Then: the profile named on the repository is the one used
      assertThat(result.output).containsIgnoringCase("in profile url-profile")
      assertThat(result.output).doesNotContain("with the service credentials")
    }

    @Test
    fun `incomplete service credentials fail with a descriptive error`() {
      // Given: only half of the service account credentials
      buildFile.writeText(printRepoCredentialsBuild(codeArtifactUrl))
      gradlePropertiesFile.writeText("systemProp.codeartifact.accessKeyId=$serviceUserAccessKeyId")

      // When: running the build
      val result = runnerWithoutAwsCredentials()
        .withArguments("printRepoCredentials")
        .buildAndFail()

      // Then: the build explains which half is missing, naming every source it could be set in
      assertThat(result.output).contains("Incomplete CodeArtifact service credentials")
      assertThat(result.output).contains("codeartifact.secretAccessKey Gradle property")
      assertThat(result.output).contains("codeartifact.secretAccessKey system property")
      assertThat(result.output).contains("CODEARTIFACT_SECRET_ACCESS_KEY")
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("ai.clarity.codeartifact.ClarityCodeartifactPluginFunctionalTest#gradleVersions")
    fun `kotlin dsl codeartifact method accepts service credentials`(gradleVersion: String) {
      // Given: a build declaring the credentials on the repository itself
      buildFile.writeText(
        """
        import ai.clarity.codeartifact.CodeArtifactCredentials
        import ai.clarity.codeartifact.codeartifact

        plugins {
            id("ai.clarity.codeartifact")
        }

        repositories {
            codeartifact(
                "$codeArtifactUrl",
                CodeArtifactCredentials.of("$serviceUserAccessKeyId", "service-user-secret")
            )
        }
        """.trimIndent()
      )

      // When: running the build (expecting a failure because the credentials are not real)
      val result = GradleRunner.create()
        .withGradleVersion(gradleVersion)
        .forwardOutput()
        .withPluginClasspath()
        .withProjectDir(projectDir)
        .withArguments("help", "--info")
        .buildAndFail()

      // Then: the token request uses the declared credentials and the secret never reaches the log
      assertThat(result.output).contains("with the service credentials $maskedServiceUserAccessKeyId")
      assertThat(result.output).doesNotContain("service-user-secret")
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("ai.clarity.codeartifact.ClarityCodeartifactPluginFunctionalTest#gradleVersions")
    fun `groovy dsl codeartifact method accepts service credentials as a map`(gradleVersion: String) {
      // Given: a Groovy build declaring the credentials on the repository itself
      settingsFile.delete()
      File(projectDir, "settings.gradle").writeText("rootProject.name = 'test-service-credentials-groovy'")
      groovyBuildFile.writeText(
        """
        plugins {
            id 'ai.clarity.codeartifact'
        }

        repositories {
            codeartifact('$codeArtifactUrl', [
                accessKeyId    : '$serviceUserAccessKeyId',
                secretAccessKey: 'service-user-secret'
            ]) {
                name = 'serviceUserRepo'
            }
        }
        """.trimIndent()
      )

      // When: running the build (expecting a failure because the credentials are not real)
      val result = GradleRunner.create()
        .withGradleVersion(gradleVersion)
        .forwardOutput()
        .withPluginClasspath()
        .withProjectDir(projectDir)
        .withArguments("help", "--info")
        .buildAndFail()

      // Then: the token request uses the declared credentials and the secret never reaches the log
      assertThat(result.output).contains("with the service credentials $maskedServiceUserAccessKeyId")
      assertThat(result.output).doesNotContain("service-user-secret")
    }

    private fun runnerWithoutAwsCredentials(): GradleRunner = GradleRunner.create()
      .forwardOutput()
      .withPluginClasspath()
      .withProjectDir(projectDir)

    private fun environmentWithoutAwsCredentials(vararg entries: Pair<String, String>): Map<String, String> =
      System.getenv().minus(
        setOf(
          "AWS_PROFILE",
          "CODEARTIFACT_PROFILE",
          "AWS_ACCESS_KEY_ID",
          "AWS_SECRET_ACCESS_KEY",
          "AWS_SESSION_TOKEN"
        )
      ) + entries
  }
}
