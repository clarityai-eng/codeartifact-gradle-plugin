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

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

// The line printRepoCredentialsBuild prints once the plugin has injected a token. The token is a fresh secret per request.
private const val injectedTokenLine = "(?m)^password=(?!null$)\\S+$"

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

// How the plugin is expected to redact an access key id in the build log
private fun masked(accessKeyId: String) = accessKeyId.take(4) + "*".repeat(accessKeyId.length - 8) + accessKeyId.takeLast(4)

/**
 * Builds whose token request reaches AWS Emulator, which checks the signature of every request. A build succeeds only
 * when the plugin signed the request with the credentials the emulator accepts, so each scenario offers the accepted
 * keys through the source under test and rejected keys through the source it must win over.
 */
class ClarityCodeArtifactGradlePluginIntegrationTest {

  @TempDir
  lateinit var projectDir: File

  private val buildFile by lazy { File(projectDir, "build.gradle.kts") }
  private val settingsFile by lazy { File(projectDir, "settings.gradle.kts") }
  private val gradlePropertiesFile by lazy { File(projectDir, "gradle.properties") }

  private val acceptedKeys = AwsEmulator.credentials
  private val rejectedKeys = AwsEmulator.rejectedCredentials

  @BeforeEach
  fun setUp() {
    settingsFile.writeText("""rootProject.name = "test-aws-emulator"""")
    gradlePropertiesFile.writeText(AwsEmulator.daemonTrustStoreProperty + "\n")
    buildFile.writeText(printRepoCredentialsBuild(AwsEmulator.repositoryUrl()))
  }

  @Nested
  inner class DefaultCredentialsChain {

    @Test
    fun `plugin fetches the token and configures the repository credentials`() {
      // Given: the accepted keys in the standard AWS variables
      val environment = AwsEmulator.buildEnvironment(
        "AWS_ACCESS_KEY_ID" to acceptedKeys.accessKeyId(),
        "AWS_SECRET_ACCESS_KEY" to acceptedKeys.secretAccessKey()
      )

      // When: running a build relying on automatic detection of the CodeArtifact url
      val result = runner(environment)
        .withArguments("printRepoCredentials")
        .build()

      // Then: the repository ends up configured with the token the service issued
      assertThat(result.output).contains("username=aws")
      assertThat(result.output).containsPattern(injectedTokenLine)
      assertThat(result.task(":printRepoCredentials")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }
  }

  @Nested
  inner class ServiceCredentials {

    @Test
    fun `service credentials from the environment sign the token request`() {
      // Given: the accepted keys only in the CodeArtifact variables, and rejected keys in the AWS ones
      val environment = AwsEmulator.buildEnvironment(
        "CODEARTIFACT_ACCESS_KEY_ID" to acceptedKeys.accessKeyId(),
        "CODEARTIFACT_SECRET_ACCESS_KEY" to acceptedKeys.secretAccessKey(),
        "AWS_ACCESS_KEY_ID" to rejectedKeys.accessKeyId(),
        "AWS_SECRET_ACCESS_KEY" to rejectedKeys.secretAccessKey()
      )

      // When: running the build
      val result = runner(environment)
        .withArguments("printRepoCredentials", "--info")
        .build()

      // Then: the service credentials signed the request, and only their masked access key id reached the log
      assertThat(result.output).contains("with the service credentials ${masked(acceptedKeys.accessKeyId())}")
      assertThat(result.output).doesNotContain(acceptedKeys.secretAccessKey())
      assertThat(result.output).contains("username=aws")
      assertThat(result.output).containsPattern(injectedTokenLine)
      assertThat(result.task(":printRepoCredentials")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `service credentials from gradle properties sign the token request`() {
      // Given: the accepted keys declared as system properties of the build, and rejected keys in the AWS variables
      gradlePropertiesFile.appendText(
        """
        systemProp.codeartifact.accessKeyId=${acceptedKeys.accessKeyId()}
        systemProp.codeartifact.secretAccessKey=${acceptedKeys.secretAccessKey()}
        """.trimIndent()
      )

      // When: running the build
      val result = runner(rejectedKeysInAwsVariables())
        .withArguments("printRepoCredentials", "--info")
        .build()

      // Then: the service credentials signed the request
      assertThat(result.output).contains("with the service credentials ${masked(acceptedKeys.accessKeyId())}")
      assertThat(result.output).doesNotContain(acceptedKeys.secretAccessKey())
      assertThat(result.output).containsPattern(injectedTokenLine)
    }

    @Test
    fun `service credentials from plain gradle properties sign the token request`() {
      // Given: the accepted keys declared without the systemProp. prefix, and rejected keys in the AWS variables
      gradlePropertiesFile.appendText(
        """
        codeartifact.accessKeyId=${acceptedKeys.accessKeyId()}
        codeartifact.secretAccessKey=${acceptedKeys.secretAccessKey()}
        """.trimIndent()
      )

      // When: running the build
      val result = runner(rejectedKeysInAwsVariables())
        .withArguments("printRepoCredentials", "--info")
        .build()

      // Then: the service credentials signed the request
      assertThat(result.output).contains("with the service credentials ${masked(acceptedKeys.accessKeyId())}")
      assertThat(result.output).doesNotContain(acceptedKeys.secretAccessKey())
      assertThat(result.output).containsPattern(injectedTokenLine)
    }

    @Test
    fun `a repository profile keeps precedence over the service credentials of the build`() {
      // Given: an accepted profile on the repository, and rejected service credentials for the whole build
      buildFile.writeText(printRepoCredentialsBuild("${AwsEmulator.repositoryUrl()}?profile=${AwsEmulator.PROFILE}"))
      gradlePropertiesFile.appendText(
        """
        systemProp.codeartifact.accessKeyId=${rejectedKeys.accessKeyId()}
        systemProp.codeartifact.secretAccessKey=${rejectedKeys.secretAccessKey()}
        """.trimIndent()
      )

      // When: running the build
      val result = runner(AwsEmulator.buildEnvironment())
        .withArguments("printRepoCredentials", "--info")
        .build()

      // Then: the profile named on the repository signed the request
      assertThat(result.output).containsIgnoringCase("in profile ${AwsEmulator.PROFILE}")
      assertThat(result.output).doesNotContain("with the service credentials")
      assertThat(result.output).containsPattern(injectedTokenLine)
    }

    @Test
    fun `rejected service credentials fail the build without leaking them to the log`() {
      // Given: service credentials the service rejects
      val environment = AwsEmulator.buildEnvironment(
        "CODEARTIFACT_ACCESS_KEY_ID" to rejectedKeys.accessKeyId(),
        "CODEARTIFACT_SECRET_ACCESS_KEY" to rejectedKeys.secretAccessKey()
      )

      // When: running the build with the most verbose logging
      val result = runner(environment, forwardOutput = false)
        .withArguments("printRepoCredentials", "--debug", "--stacktrace")
        .buildAndFail()

      // Then: the request was refused and the secret never reached the log. The full access key id does: at debug level
      // the HTTP client of the AWS SDK logs the Authorization header, whose SigV4 credential scope carries it.
      assertThat(result.output).contains("with the service credentials ${masked(rejectedKeys.accessKeyId())}")
      assertThat(result.output).contains("Status Code: 403")
      assertThat(result.output).doesNotContain(rejectedKeys.secretAccessKey())
    }

    private fun rejectedKeysInAwsVariables() = AwsEmulator.buildEnvironment(
      "AWS_ACCESS_KEY_ID" to rejectedKeys.accessKeyId(),
      "AWS_SECRET_ACCESS_KEY" to rejectedKeys.secretAccessKey()
    )
  }

  private fun runner(environment: Map<String, String>, forwardOutput: Boolean = true): GradleRunner =
    GradleRunner.create()
      .apply { if (forwardOutput) forwardOutput() }
      .withPluginClasspath()
      .withProjectDir(projectDir)
      .withEnvironment(environment)
}
