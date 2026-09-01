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

import org.gradle.api.logging.Logger
import org.gradle.api.provider.ProviderFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads one `codeartifact.*` setting from the sources the build may configure it in.
 *
 * It is an interface so that the tests can exercise every source without mutating the JVM.
 */
internal fun interface SettingLookup {

  /**
   * Returns the value configured as the [property] Gradle property, the [property] system property or the
   * [environmentVariable] environment variable, or `null` when none of them holds a non-blank value.
   */
  fun read(property: String, environmentVariable: String): String?

  companion object {

    /**
     * Reads the settings through [providers], from the closest source to the build invocation first:
     *
     *  1. the Gradle property, written plainly in `gradle.properties` or passed as `-P`
     *  2. the Java system property, written as `systemProp.` in `gradle.properties` or passed as `-D`
     *  3. the environment variable
     *
     * A source that holds a value wins even when that value is blank, and a blank value resolves to `null`: a setting
     * left empty on purpose disables the ones below it rather than falling through to them.
     *
     * Because the Gradle property now outranks the sources that used to win, a `-D` on the command line or an
     * exported environment variable no longer overrides a plain entry in `gradle.properties`. That reads as the
     * override being ignored, so [logger] gets a warning whenever the Gradle property disagrees with the source it
     * shadows: the system property, or — only when no system property is set, since the system property already
     * outranked the environment before — the environment variable.
     */
    fun of(providers: ProviderFactory, logger: Logger): SettingLookup {
      val warned = ConcurrentHashMap.newKeySet<String>()

      return SettingLookup { property, environmentVariable ->
        val gradleProperty = providers.gradleProperty(property).orNull
        val systemProperty = providers.systemProperty(property).orNull
        // Both resolution and the warning need the environment only when there is no system property
        val environment = if (systemProperty == null) providers.environmentVariable(environmentVariable).orNull else null

        val shadowingWarning = when {
          gradleProperty == null -> null
          systemProperty != null && systemProperty != gradleProperty -> shadowedSystemPropertyWarning(property)
          environment != null && environment != gradleProperty ->
            shadowedEnvironmentVariableWarning(property, environmentVariable)
          else -> null
        }
        if (shadowingWarning != null && warned.add(property)) {
          logger.warn(shadowingWarning)
        }

        (gradleProperty ?: systemProperty ?: environment)?.takeIf { it.isNotBlank() }
      }
    }

    /**
     * The values are deliberately left out: a shadowed `codeartifact.secretAccessKey` would print the secret.
     */
    fun shadowedSystemPropertyWarning(property: String) =
      "The $property Gradle property takes precedence over the $property system property, which holds a different " +
        "value and is being ignored. Override the Gradle property with -P$property, or remove it to let the system " +
        "property apply."

    /**
     * The values are deliberately left out: a shadowed `CODEARTIFACT_SECRET_ACCESS_KEY` would print the secret.
     */
    fun shadowedEnvironmentVariableWarning(property: String, environmentVariable: String) =
      "The $property Gradle property takes precedence over the $environmentVariable environment variable, which " +
        "holds a different value and is being ignored. Override the Gradle property with -P$property, or remove it " +
        "to let the environment variable apply."
  }
}
