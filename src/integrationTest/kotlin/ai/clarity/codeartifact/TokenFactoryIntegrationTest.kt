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
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.services.codeartifact.model.CodeartifactException
import software.amazon.awssdk.services.codeartifact.model.GetAuthorizationTokenResponse
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass

/**
 * What a token request granted. The token itself is a fresh secret on every call, so only whether one came back is
 * compared.
 */
private data class IssuedToken(val tokenIssued: Boolean, val validForMinutes: Long) {

  companion object {
    /** CodeArtifact issues a token for 12 hours when the request names no duration. */
    val DEFAULT_LIFETIME = IssuedToken(tokenIssued = true, validForMinutes = 12 * 60)

    fun of(response: GetAuthorizationTokenResponse, requestedAt: Instant) = IssuedToken(
      tokenIssued = !response.authorizationToken().isNullOrBlank(),
      // Rounded to the minute: the emulator counts the expiration from its own clock, slightly after requestedAt
      validForMinutes = Duration.between(requestedAt, response.expiration()).plusSeconds(30).toMinutes()
    )
  }
}

/** How a token request failed, or nulls when it did not fail. */
private data class ServiceFailure(val type: KClass<out Throwable>?, val statusCode: Int?) {

  companion object {
    fun of(thrown: Throwable?) = ServiceFailure(thrown?.let { it::class }, (thrown as? AwsServiceException)?.statusCode())
  }
}

class TokenFactoryIntegrationTest {

  @Nested
  inner class WithAnAwsProfile {

    @Test
    fun `issues a token signed with the credentials of the profile`() {
      // Given: a profile holding the key pair the service accepts
      val requestedAt = Instant.now()

      // When: requesting a token for a repository of an existing domain
      val response = TokenFactory.getAuthorizationToken(AwsEmulator.repositoryUrl(), AwsEmulator.PROFILE)

      // Then: the service grants a token for the default lifetime
      assertThat(IssuedToken.of(response, requestedAt)).isEqualTo(IssuedToken.DEFAULT_LIFETIME)
    }

    @Test
    fun `fails when the profile holds keys the service does not accept`() {
      // Given: a profile holding unknown keys

      // When: requesting a token with it
      val thrown = catchThrowable { TokenFactory.getAuthorizationToken(AwsEmulator.repositoryUrl(), AwsEmulator.REJECTED_PROFILE) }

      // Then: the service refuses the request
      assertThat(ServiceFailure.of(thrown)).isEqualTo(ServiceFailure(CodeartifactException::class, 403))
    }
  }

  @Nested
  inner class WithServiceCredentials {

    @Test
    fun `issues a token signed with the service credentials`() {
      // Given: the static credentials of a service account the service accepts
      val credentials = CodeArtifactCredentials.of(AwsEmulator.credentials.accessKeyId(), AwsEmulator.credentials.secretAccessKey())
      val requestedAt = Instant.now()

      // When: requesting a token with them
      val response = TokenFactory.getAuthorizationToken(CodeArtifactUrl.of(AwsEmulator.repositoryUrl()), credentials)

      // Then: the service grants a token for the default lifetime
      assertThat(IssuedToken.of(response, requestedAt)).isEqualTo(IssuedToken.DEFAULT_LIFETIME)
    }

    @Test
    fun `fails when the secret access key does not match the access key id`() {
      // Given: a known access key id with a wrong secret
      val credentials = CodeArtifactCredentials.of(
        AwsEmulator.credentials.accessKeyId(),
        AwsEmulator.rejectedCredentials.secretAccessKey()
      )

      // When: requesting a token with them
      val thrown = catchThrowable { TokenFactory.getAuthorizationToken(CodeArtifactUrl.of(AwsEmulator.repositoryUrl()), credentials) }

      // Then: the service refuses the signature
      assertThat(ServiceFailure.of(thrown)).isEqualTo(ServiceFailure(CodeartifactException::class, 403))
    }
  }

  @Nested
  inner class WithTheDefaultCredentialsChain {

    @Test
    fun `issues a token signed with the credentials the chain resolves`() {
      // Given: no profile, so the default chain resolves the default profile of the shared credentials file
      val requestedAt = Instant.now()

      // When: requesting a token without a profile
      val response = TokenFactory.getAuthorizationToken(AwsEmulator.repositoryUrl(), null)

      // Then: the service grants a token for the default lifetime
      assertThat(IssuedToken.of(response, requestedAt)).isEqualTo(IssuedToken.DEFAULT_LIFETIME)
    }
  }

  @Nested
  inner class ForADomainThatDoesNotExist {

    @Test
    fun `fails with the not found error of the service`() {
      // Given: a repository url whose domain the account does not hold
      val repositoryUrl = AwsEmulator.repositoryUrl(domain = "missing-domain")

      // When: requesting a token for it
      val thrown = catchThrowable { TokenFactory.getAuthorizationToken(repositoryUrl, AwsEmulator.PROFILE) }

      // Then: the service answers that the domain is not found
      assertThat(ServiceFailure.of(thrown)).isEqualTo(ServiceFailure(ResourceNotFoundException::class, 404))
    }
  }
}
