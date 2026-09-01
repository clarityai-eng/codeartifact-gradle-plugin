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

import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.logging.Logger
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Exercises the lookup against a real [org.gradle.api.provider.ProviderFactory], so that the Gradle property and
 * system property sources are the ones a build actually sees. The environment variable leg cannot be set from a unit
 * test; the functional tests cover it through `GradleRunner.withEnvironment`.
 */
class SettingLookupTest {

  private val logger = mockk<Logger>(relaxed = true)

  @AfterEach
  fun tearDown() {
    System.clearProperty(PROFILE)
  }

  private fun lookupOf(projectDir: File, gradleProperties: String = ""): SettingLookup {
    File(projectDir, "gradle.properties").writeText(gradleProperties)
    val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
    return SettingLookup.of(project.providers, logger)
  }

  private fun SettingLookup.readProfile() = read(PROFILE, PROFILE_ENV)

  @Test
  fun `the gradle property takes precedence over the system property`(@TempDir projectDir: File) {
    // Given
    System.setProperty(PROFILE, "from-system-property")
    val lookup = lookupOf(projectDir, "$PROFILE=from-gradle-property")

    // When / Then
    assertThat(lookup.readProfile()).isEqualTo("from-gradle-property")
  }

  @Test
  fun `the system property is used when there is no gradle property`(@TempDir projectDir: File) {
    // Given
    System.setProperty(PROFILE, "from-system-property")
    val lookup = lookupOf(projectDir)

    // When / Then
    assertThat(lookup.readProfile()).isEqualTo("from-system-property")
  }

  @Test
  fun `the environment variable is used when neither property is set`(@TempDir projectDir: File) {
    // Given: PATH is the one environment variable a unit test can count on being set
    val lookup = lookupOf(projectDir)

    // When / Then
    assertThat(lookup.read(PROFILE, "PATH")).isEqualTo(System.getenv("PATH"))
  }

  @Test
  fun `nothing is resolved when no source is set`(@TempDir projectDir: File) {
    assertThat(lookupOf(projectDir).read(PROFILE, "CODEARTIFACT_ABSENT_ENVIRONMENT_VARIABLE")).isNull()
  }

  @Test
  fun `a blank gradle property resolves to null instead of falling through`(@TempDir projectDir: File) {
    // Given: a setting emptied on purpose disables the sources below it
    System.setProperty(PROFILE, "from-system-property")
    val lookup = lookupOf(projectDir, "$PROFILE=")

    // When / Then
    assertThat(lookup.readProfile()).isNull()
  }

  @Test
  fun `a gradle property shadowing a different system property warns once`(@TempDir projectDir: File) {
    // Given
    System.setProperty(PROFILE, "ci")
    val lookup = lookupOf(projectDir, "$PROFILE=dev")

    // When: the configurer reads the same setting once per repository
    lookup.readProfile()
    lookup.readProfile()

    // Then
    verify(exactly = 1) { logger.warn(SettingLookup.shadowedSystemPropertyWarning(PROFILE)) }
  }

  @Test
  fun `no warning when the gradle property and the system property agree`(@TempDir projectDir: File) {
    // Given
    System.setProperty(PROFILE, "dev")
    val lookup = lookupOf(projectDir, "$PROFILE=dev")

    // When
    lookup.readProfile()

    // Then
    verify(exactly = 0) { logger.warn(SettingLookup.shadowedSystemPropertyWarning(PROFILE)) }
  }

  @Test
  fun `no warning when only the gradle property is set`(@TempDir projectDir: File) {
    // Given
    val lookup = lookupOf(projectDir, "$PROFILE=dev")

    // When
    lookup.readProfile()

    // Then
    verify(exactly = 0) { logger.warn(SettingLookup.shadowedSystemPropertyWarning(PROFILE)) }
  }

  @Test
  fun `the warning never names the value, so a shadowed secret cannot leak`() {
    assertThat(SettingLookup.shadowedSystemPropertyWarning("codeartifact.secretAccessKey")).isEqualTo(
      "The codeartifact.secretAccessKey Gradle property takes precedence over the codeartifact.secretAccessKey " +
        "system property, which holds a different value and is being ignored. Override the Gradle property with " +
        "-Pcodeartifact.secretAccessKey, or remove it to let the system property apply."
    )
  }

  private companion object {
    const val PROFILE = "codeartifact.profile"
    const val PROFILE_ENV = "CODEARTIFACT_PROFILE"
  }
}
