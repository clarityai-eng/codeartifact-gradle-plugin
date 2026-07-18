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

      // Stub of the CodeArtifact GetAuthorizationToken endpoint
      server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
      server.createContext("/") { exchange ->
        val body = """{"authorizationToken":"stub-token","expiration":1893456000}"""
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
      }
      server.start()
    }

    @AfterEach
    fun tearDown() {
      server.stop(0)
    }

    @Test
    fun `plugin fetches the token from the codeartifact endpoint and configures credentials`() {
      // Given: a build file relying on automatic detection of the CodeArtifact URL
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

        tasks.register("printRepoCredentials") {
            doLast {
                val repo = project.repositories.first() as org.gradle.api.artifacts.repositories.MavenArtifactRepository
                println("username=${repo.credentials.username}")
                println("password=${repo.credentials.password}")
            }
        }
        """.trimIndent()
      )

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
}
