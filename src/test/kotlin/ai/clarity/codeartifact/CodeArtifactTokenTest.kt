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

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.codeartifact.model.GetAuthorizationTokenResponse
import java.net.URI

class CodeArtifactTokenTest {

  private val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"
  private val credentials = CodeArtifactCredentials.of("AKIAIOSFODNN7EXAMPLE", "secret")

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `token is fetched only once for the same url and profile`() {
    // Given
    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "default") } returns
      GetAuthorizationTokenResponse.builder().authorizationToken("tok-1").build()

    val service = CodeArtifactToken()

    // When
    val first = service.getToken(codeArtifactUrl, "default")
    val second = service.getToken(codeArtifactUrl, "default")

    // Then
    assertThat(first).isEqualTo("tok-1")
    assertThat(second).isEqualTo("tok-1")
    verify(exactly = 1) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "default") }
  }

  @Test
  fun `token is fetched separately for each profile on the same url`() {
    // Given
    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "dev") } returns
      GetAuthorizationTokenResponse.builder().authorizationToken("tok-dev").build()
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "prod") } returns
      GetAuthorizationTokenResponse.builder().authorizationToken("tok-prod").build()

    val service = CodeArtifactToken()

    // When
    val devToken = service.getToken(codeArtifactUrl, "dev")
    val prodToken = service.getToken(codeArtifactUrl, "prod")

    // Then
    assertThat(devToken).isEqualTo("tok-dev")
    assertThat(prodToken).isEqualTo("tok-prod")
    verify(exactly = 1) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "dev") }
    verify(exactly = 1) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "prod") }
  }

  @Test
  fun `token is fetched separately for each url on the same profile`() {
    // Given
    val otherUrl = "https://other-domain-444455556666.d.codeartifact.eu-west-1.amazonaws.com/maven/other-repo/"

    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "default") } returnsMany listOf(
      GetAuthorizationTokenResponse.builder().authorizationToken("tok-1").build(),
      GetAuthorizationTokenResponse.builder().authorizationToken("tok-2").build()
    )

    val service = CodeArtifactToken()

    // When
    val first = service.getToken(codeArtifactUrl, "default")
    val second = service.getToken(otherUrl, "default")

    // Then
    assertThat(first).isEqualTo("tok-1")
    assertThat(second).isEqualTo("tok-2")
    verify(exactly = 2) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "default") }
  }

  @Test
  fun `getToken accepts a URI and shares the cache with the string overload`() {
    // Given
    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "default") } returns
      GetAuthorizationTokenResponse.builder().authorizationToken("tok-uri").build()

    val service = CodeArtifactToken()

    // When
    val fromUri = service.getToken(URI(codeArtifactUrl), "default")
    val fromString = service.getToken(codeArtifactUrl, "default")

    // Then
    assertThat(fromUri).isEqualTo("tok-uri")
    assertThat(fromString).isEqualTo("tok-uri")
    verify(exactly = 1) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "default") }
  }

  @Test
  fun `token is fetched only once for the same url and service credentials`() {
    // Given
    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), credentials) } returns
      GetAuthorizationTokenResponse.builder().authorizationToken("tok-credentials").build()

    val service = CodeArtifactToken()

    // When
    val first = service.getToken(codeArtifactUrl, credentials)
    val second = service.getToken(URI(codeArtifactUrl), credentials)

    // Then
    assertThat(first).isEqualTo("tok-credentials")
    assertThat(second).isEqualTo("tok-credentials")
    verify(exactly = 1) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), credentials) }
  }

  @Test
  fun `token is fetched separately for each set of service credentials on the same url`() {
    // Given
    val otherCredentials = CodeArtifactCredentials.of("AKIAANOTHEREXAMPLE00", "other-secret")

    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), credentials) } returns
      GetAuthorizationTokenResponse.builder().authorizationToken("tok-service-user").build()
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), otherCredentials) } returns
      GetAuthorizationTokenResponse.builder().authorizationToken("tok-other-service-user").build()

    val service = CodeArtifactToken()

    // When
    val token = service.getToken(codeArtifactUrl, credentials)
    val otherToken = service.getToken(codeArtifactUrl, otherCredentials)

    // Then
    assertThat(token).isEqualTo("tok-service-user")
    assertThat(otherToken).isEqualTo("tok-other-service-user")
  }

  @Test
  fun `service credentials and profiles do not share cache entries`() {
    // Given
    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "default") } returns
      GetAuthorizationTokenResponse.builder().authorizationToken("tok-profile").build()
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), credentials) } returns
      GetAuthorizationTokenResponse.builder().authorizationToken("tok-credentials").build()

    val service = CodeArtifactToken()

    // When
    val profileToken = service.getToken(codeArtifactUrl, "default")
    val credentialsToken = service.getToken(codeArtifactUrl, credentials)

    // Then
    assertThat(profileToken).isEqualTo("tok-profile")
    assertThat(credentialsToken).isEqualTo("tok-credentials")
  }
}
