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

import org.gradle.api.InvalidUserDataException

/**
 * Resolves the service account credentials shared by the whole build, from system properties first and environment
 * variables second.
 *
 * They are deliberately not read from the repository URL: a query param would leak the secret access key into build
 * scans, caches and logs.
 */
internal object CodeArtifactCredentialsResolver {

  private val ACCESS_KEY_ID = Source("codeartifact.accessKeyId", "CODEARTIFACT_ACCESS_KEY_ID")
  private val SECRET_ACCESS_KEY = Source("codeartifact.secretAccessKey", "CODEARTIFACT_SECRET_ACCESS_KEY")
  private val SESSION_TOKEN = Source("codeartifact.sessionToken", "CODEARTIFACT_SESSION_TOKEN")

  /**
   * Returns the configured credentials, or `null` when none is configured and the profile based authentication has to
   * be used instead.
   *
   * The lookups are parameters so that the tests can exercise the environment variables without mutating the JVM.
   */
  fun resolve(
    systemProperties: (String) -> String? = System::getProperty,
    environment: (String) -> String? = System::getenv
  ): CodeArtifactCredentials? {
    val accessKeyId = ACCESS_KEY_ID.read(systemProperties, environment)
    val secretAccessKey = SECRET_ACCESS_KEY.read(systemProperties, environment)

    if (accessKeyId == null && secretAccessKey == null) {
      return null
    }
    if (accessKeyId == null) {
      throw incompleteCredentials(ACCESS_KEY_ID)
    }
    if (secretAccessKey == null) {
      throw incompleteCredentials(SECRET_ACCESS_KEY)
    }

    return CodeArtifactCredentials.of(accessKeyId, secretAccessKey, SESSION_TOKEN.read(systemProperties, environment))
  }

  private fun incompleteCredentials(missing: Source) = InvalidUserDataException(
    "Incomplete CodeArtifact service credentials: neither the ${missing.systemProperty} system property nor the " +
      "${missing.environmentVariable} environment variable is set. Configure both the access key id and the secret " +
      "access key, or unset them to authenticate with an AWS profile."
  )

  private data class Source(val systemProperty: String, val environmentVariable: String) {

    fun read(systemProperties: (String) -> String?, environment: (String) -> String?): String? =
      (systemProperties(systemProperty) ?: environment(environmentVariable))?.takeIf { it.isNotBlank() }
  }
}
