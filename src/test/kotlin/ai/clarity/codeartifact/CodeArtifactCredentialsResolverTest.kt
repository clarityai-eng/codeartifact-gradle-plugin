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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.InvalidUserDataException
import org.junit.jupiter.api.Test

class CodeArtifactCredentialsResolverTest {

  private fun resolve(systemProperties: Map<String, String> = emptyMap(), environment: Map<String, String> = emptyMap()) =
    CodeArtifactCredentialsResolver.resolve(systemProperties::get, environment::get)

  @Test
  fun `no credentials are resolved when nothing is configured`() {
    assertThat(resolve()).isNull()
  }

  @Test
  fun `credentials are resolved from the system properties`() {
    // When
    val credentials = resolve(
      systemProperties = mapOf(
        "codeartifact.accessKeyId" to "sysprop-key-id",
        "codeartifact.secretAccessKey" to "sysprop-secret"
      )
    )

    // Then
    assertThat(credentials).isEqualTo(CodeArtifactCredentials.of("sysprop-key-id", "sysprop-secret"))
  }

  @Test
  fun `credentials are resolved from the environment variables`() {
    // When
    val credentials = resolve(
      environment = mapOf(
        "CODEARTIFACT_ACCESS_KEY_ID" to "env-key-id",
        "CODEARTIFACT_SECRET_ACCESS_KEY" to "env-secret",
        "CODEARTIFACT_SESSION_TOKEN" to "env-session-token"
      )
    )

    // Then
    assertThat(credentials).isEqualTo(CodeArtifactCredentials.of("env-key-id", "env-secret", "env-session-token"))
  }

  @Test
  fun `each system property takes precedence over its environment variable`() {
    // When
    val credentials = resolve(
      systemProperties = mapOf("codeartifact.accessKeyId" to "sysprop-key-id"),
      environment = mapOf(
        "CODEARTIFACT_ACCESS_KEY_ID" to "env-key-id",
        "CODEARTIFACT_SECRET_ACCESS_KEY" to "env-secret"
      )
    )

    // Then
    assertThat(credentials).isEqualTo(CodeArtifactCredentials.of("sysprop-key-id", "env-secret"))
  }

  @Test
  fun `the session token is optional`() {
    // When
    val credentials = resolve(
      systemProperties = mapOf(
        "codeartifact.accessKeyId" to "key-id",
        "codeartifact.secretAccessKey" to "secret"
      )
    )

    // Then
    assertThat(credentials).isEqualTo(CodeArtifactCredentials.of("key-id", "secret"))
  }

  @Test
  fun `blank values are ignored`() {
    assertThat(
      resolve(systemProperties = mapOf("codeartifact.accessKeyId" to "  ", "codeartifact.secretAccessKey" to "  "))
    ).isNull()
  }

  @Test
  fun `a secret access key without an access key id is rejected`() {
    assertThatThrownBy { resolve(systemProperties = mapOf("codeartifact.secretAccessKey" to "secret")) }
      .isInstanceOf(InvalidUserDataException::class.java)
      .hasMessageContaining("codeartifact.accessKeyId")
      .hasMessageContaining("CODEARTIFACT_ACCESS_KEY_ID")
  }

  @Test
  fun `an access key id without a secret access key is rejected`() {
    assertThatThrownBy { resolve(environment = mapOf("CODEARTIFACT_ACCESS_KEY_ID" to "key-id")) }
      .isInstanceOf(InvalidUserDataException::class.java)
      .hasMessageContaining("codeartifact.secretAccessKey")
      .hasMessageContaining("CODEARTIFACT_SECRET_ACCESS_KEY")
  }
}
