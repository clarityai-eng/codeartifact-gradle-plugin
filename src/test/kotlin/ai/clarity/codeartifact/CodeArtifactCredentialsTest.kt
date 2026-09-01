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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials

private const val ACCESS_KEY_ID = "AKIAIOSFODNN7EXAMPLE"
private const val SECRET_ACCESS_KEY = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"

class CodeArtifactCredentialsTest {

  @Nested
  inner class CreationTest {

    @Test
    fun `credentials without session token map to basic aws credentials`() {
      // When
      val credentials = CodeArtifactCredentials.of(ACCESS_KEY_ID, SECRET_ACCESS_KEY)

      // Then
      val awsCredentials = credentials.toAwsCredentials()
      assertThat(credentials.accessKeyId).isEqualTo(ACCESS_KEY_ID)
      assertThat(awsCredentials.accessKeyId()).isEqualTo(ACCESS_KEY_ID)
      assertThat(awsCredentials.secretAccessKey()).isEqualTo(SECRET_ACCESS_KEY)
      assertThat(awsCredentials).isNotInstanceOf(AwsSessionCredentials::class.java)
    }

    @Test
    fun `credentials with session token map to temporary aws credentials`() {
      // When
      val credentials = CodeArtifactCredentials.of(ACCESS_KEY_ID, SECRET_ACCESS_KEY, "session-token")

      // Then
      val awsCredentials = credentials.toAwsCredentials()
      assertThat(awsCredentials).isInstanceOf(AwsSessionCredentials::class.java)
      assertThat((awsCredentials as AwsSessionCredentials).sessionToken()).isEqualTo("session-token")
    }

    @Test
    fun `a blank session token is treated as absent`() {
      // When
      val credentials = CodeArtifactCredentials.of(ACCESS_KEY_ID, SECRET_ACCESS_KEY, "  ")

      // Then
      assertThat(credentials.toAwsCredentials()).isNotInstanceOf(AwsSessionCredentials::class.java)
      assertThat(credentials).isEqualTo(CodeArtifactCredentials.of(ACCESS_KEY_ID, SECRET_ACCESS_KEY))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun `a missing access key id is rejected`(accessKeyId: String) {
      assertThatThrownBy { CodeArtifactCredentials.of(accessKeyId, SECRET_ACCESS_KEY) }
        .isInstanceOf(IllegalArgumentException::class.java)
        .hasMessageContaining("accessKeyId")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun `a missing secret access key is rejected`(secretAccessKey: String) {
      assertThatThrownBy { CodeArtifactCredentials.of(ACCESS_KEY_ID, secretAccessKey) }
        .isInstanceOf(IllegalArgumentException::class.java)
        .hasMessageContaining("secretAccessKey")
    }
  }

  @Nested
  inner class MapCreationTest {

    @Test
    fun `credentials are built from a map`() {
      // When
      val credentials = CodeArtifactCredentials.of(
        mapOf(
          "accessKeyId" to ACCESS_KEY_ID,
          "secretAccessKey" to SECRET_ACCESS_KEY,
          "sessionToken" to "session-token"
        )
      )

      // Then
      assertThat(credentials).isEqualTo(CodeArtifactCredentials.of(ACCESS_KEY_ID, SECRET_ACCESS_KEY, "session-token"))
    }

    @Test
    fun `the session token entry is optional`() {
      // When
      val credentials = CodeArtifactCredentials.of(
        mapOf("accessKeyId" to ACCESS_KEY_ID, "secretAccessKey" to SECRET_ACCESS_KEY)
      )

      // Then
      assertThat(credentials).isEqualTo(CodeArtifactCredentials.of(ACCESS_KEY_ID, SECRET_ACCESS_KEY))
    }

    @Test
    fun `an unsupported entry is rejected`() {
      assertThatThrownBy {
        CodeArtifactCredentials.of(
          mapOf("accessKeyId" to ACCESS_KEY_ID, "secretAccessKey" to SECRET_ACCESS_KEY, "profile" to "prod")
        )
      }
        .isInstanceOf(IllegalArgumentException::class.java)
        .hasMessage(
          "Unsupported CodeArtifact credentials [profile], " +
            "expected any of [accessKeyId, secretAccessKey, sessionToken]"
        )
    }

    @Test
    fun `any char sequence is accepted, as Groovy passes GStrings`() {
      // When
      val credentials = CodeArtifactCredentials.of(
        mapOf("accessKeyId" to StringBuilder(ACCESS_KEY_ID), "secretAccessKey" to StringBuilder(SECRET_ACCESS_KEY))
      )

      // Then
      assertThat(credentials).isEqualTo(CodeArtifactCredentials.of(ACCESS_KEY_ID, SECRET_ACCESS_KEY))
    }

    @Test
    fun `a value that is not a char sequence is rejected without disclosing it`() {
      assertThatThrownBy {
        CodeArtifactCredentials.of(mapOf("accessKeyId" to ACCESS_KEY_ID, "secretAccessKey" to 1234))
      }
        .isInstanceOf(IllegalArgumentException::class.java)
        .hasMessageContaining("secretAccessKey")
        .hasMessageContaining("java.lang.Integer")
        .hasMessageNotContaining("1234")
    }

    @Test
    fun `a map without the secret access key is rejected`() {
      assertThatThrownBy { CodeArtifactCredentials.of(mapOf("accessKeyId" to ACCESS_KEY_ID)) }
        .isInstanceOf(IllegalArgumentException::class.java)
        .hasMessageContaining("secretAccessKey")
    }
  }

  @Nested
  inner class RedactionTest {

    @Test
    fun `the masked access key id keeps only its edges`() {
      assertThat(CodeArtifactCredentials.of(ACCESS_KEY_ID, SECRET_ACCESS_KEY).maskedAccessKeyId)
        .isEqualTo("AKIA************MPLE")
    }

    @Test
    fun `a short access key id is masked entirely`() {
      assertThat(CodeArtifactCredentials.of("AKIA1234", SECRET_ACCESS_KEY).maskedAccessKeyId).isEqualTo("********")
    }

    @Test
    fun `toString never exposes the secrets`() {
      // When
      val description = CodeArtifactCredentials.of(ACCESS_KEY_ID, SECRET_ACCESS_KEY, "session-token").toString()

      // Then
      assertThat(description).doesNotContain(SECRET_ACCESS_KEY, "session-token", ACCESS_KEY_ID)
      assertThat(description).contains("AKIA************MPLE", "sessionToken=present")
    }
  }

  @Nested
  inner class CacheKeyTest {

    @Test
    fun `equal credentials share the cache key`() {
      assertThat(CodeArtifactCredentials.of(ACCESS_KEY_ID, SECRET_ACCESS_KEY).cacheKey())
        .isEqualTo(CodeArtifactCredentials.of(ACCESS_KEY_ID, SECRET_ACCESS_KEY).cacheKey())
    }

    @Test
    fun `the cache key never contains the secrets`() {
      // When
      val cacheKey = CodeArtifactCredentials.of(ACCESS_KEY_ID, SECRET_ACCESS_KEY, "session-token").cacheKey()

      // Then
      assertThat(cacheKey).doesNotContain(SECRET_ACCESS_KEY, "session-token", ACCESS_KEY_ID)
    }

    @Test
    fun `credentials differing only by the secret do not share the cache key`() {
      assertThat(CodeArtifactCredentials.of(ACCESS_KEY_ID, SECRET_ACCESS_KEY).cacheKey())
        .isNotEqualTo(CodeArtifactCredentials.of(ACCESS_KEY_ID, "another-secret").cacheKey())
    }

    @Test
    fun `credentials differing only by the session token do not share the cache key`() {
      assertThat(CodeArtifactCredentials.of(ACCESS_KEY_ID, SECRET_ACCESS_KEY).cacheKey())
        .isNotEqualTo(CodeArtifactCredentials.of(ACCESS_KEY_ID, SECRET_ACCESS_KEY, "session-token").cacheKey())
    }
  }

  @Nested
  inner class EqualityTest {

    @Test
    fun `credentials are compared by value`() {
      assertThat(CodeArtifactCredentials.of(ACCESS_KEY_ID, SECRET_ACCESS_KEY))
        .isEqualTo(CodeArtifactCredentials.of(ACCESS_KEY_ID, SECRET_ACCESS_KEY))
        .hasSameHashCodeAs(CodeArtifactCredentials.of(ACCESS_KEY_ID, SECRET_ACCESS_KEY))
        .isNotEqualTo(CodeArtifactCredentials.of("AKIAANOTHEREXAMPLE00", SECRET_ACCESS_KEY))
    }
  }
}
