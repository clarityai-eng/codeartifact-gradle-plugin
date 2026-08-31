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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.codeartifact.model.GetAuthorizationTokenResponse
import java.net.URI

/**
 * A simple unit test for the 'ai.clarity.codeartifact' plugin.
 */
class ClarityCodeArtifactGradlePluginTest {

  @Nested
  inner class PluginApplicationTest {

    @Test
    fun `plugin applies successfully`() {
      // Given
      val project = ProjectBuilder.builder().build()

      // When
      project.plugins.apply("ai.clarity.codeartifact")

      // Then
      assertThat(project.plugins.hasPlugin("ai.clarity.codeartifact")).isTrue
    }
  }

  @Nested
  inner class KotlinDslExtensionTest {

    @AfterEach
    fun tearDown() {
      unmockkAll()
      System.clearProperty("codeartifact.accessKeyId")
      System.clearProperty("codeartifact.secretAccessKey")
    }

    @Test
    fun `codeartifact extension configures repository correctly`() {
      // Given
      val mockResponse = GetAuthorizationTokenResponse.builder()
        .authorizationToken("mock-token")
        .build()

      mockkStatic(TokenFactory::class)
      every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "default") } returns mockResponse

      val project = ProjectBuilder.builder().build()
      project.plugins.apply("ai.clarity.codeartifact")

      val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

      // When
      project.repositories.codeartifact(codeArtifactUrl)

      // Then
      val repository = project.repositories.first() as MavenArtifactRepository
      assertThat(repository.url).isEqualTo(URI(codeArtifactUrl))
      assertThat(repository.credentials.username).isEqualTo("aws")
      assertThat(repository.credentials.password).isEqualTo("mock-token")

      verify(exactly = 1) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "default") }
    }

    @Test
    fun `codeartifact extension configures repository with profile correctly`() {
      // Given
      val mockResponse = GetAuthorizationTokenResponse.builder()
        .authorizationToken("mock-token-profile")
        .build()

      mockkStatic(TokenFactory::class)
      every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "my-profile") } returns mockResponse

      val project = ProjectBuilder.builder().build()
      project.plugins.apply("ai.clarity.codeartifact")

      val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

      // When
      project.repositories.codeartifact(codeArtifactUrl, "my-profile")

      // Then
      val repository = project.repositories.first() as MavenArtifactRepository
      assertThat(repository.url).isEqualTo(URI(codeArtifactUrl))
      assertThat(repository.credentials.username).isEqualTo("aws")
      assertThat(repository.credentials.password).isEqualTo("mock-token-profile")

      verify(exactly = 1) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "my-profile") }
    }

    @Test
    fun `codeartifact extension configures repository with service credentials`() {
      // Given
      val credentials = CodeArtifactCredentials.of("AKIAIOSFODNN7EXAMPLE", "service-user-secret")
      val mockResponse = GetAuthorizationTokenResponse.builder()
        .authorizationToken("mock-token-service-user")
        .build()

      mockkStatic(TokenFactory::class)
      every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), credentials) } returns mockResponse

      val project = ProjectBuilder.builder().build()
      project.plugins.apply("ai.clarity.codeartifact")

      val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

      // When
      project.repositories.codeartifact(codeArtifactUrl, credentials) {
        it.name = "serviceUserRepo"
      }

      // Then
      val repository = project.repositories.first() as MavenArtifactRepository
      assertThat(repository.name).isEqualTo("serviceUserRepo")
      assertThat(repository.url).isEqualTo(URI(codeArtifactUrl))
      assertThat(repository.credentials.username).isEqualTo("aws")
      assertThat(repository.credentials.password).isEqualTo("mock-token-service-user")

      verify(exactly = 1) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), credentials) }
      verify(exactly = 0) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<String>()) }
    }

    @Test
    fun `codeartifact extension without a profile uses the service credentials of the build`() {
      // Given
      System.setProperty("codeartifact.accessKeyId", "AKIAIOSFODNN7EXAMPLE")
      System.setProperty("codeartifact.secretAccessKey", "service-user-secret")

      val credentials = CodeArtifactCredentials.of("AKIAIOSFODNN7EXAMPLE", "service-user-secret")

      mockkStatic(TokenFactory::class)
      every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), credentials) } returns
        GetAuthorizationTokenResponse.builder().authorizationToken("mock-token-service-user").build()

      val project = ProjectBuilder.builder().build()
      project.plugins.apply("ai.clarity.codeartifact")

      val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

      // When
      project.repositories.codeartifact(codeArtifactUrl)

      // Then
      val repository = project.repositories.first() as MavenArtifactRepository
      assertThat(repository.credentials.password).isEqualTo("mock-token-service-user")

      verify(exactly = 0) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<String>()) }
    }

    @Test
    fun `an explicit profile takes precedence over the service credentials of the build`() {
      // Given
      System.setProperty("codeartifact.accessKeyId", "AKIAIOSFODNN7EXAMPLE")
      System.setProperty("codeartifact.secretAccessKey", "service-user-secret")

      mockkStatic(TokenFactory::class)
      every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "my-profile") } returns
        GetAuthorizationTokenResponse.builder().authorizationToken("mock-token-profile").build()

      val project = ProjectBuilder.builder().build()
      project.plugins.apply("ai.clarity.codeartifact")

      val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

      // When
      project.repositories.codeartifact(codeArtifactUrl, "my-profile")

      // Then
      val repository = project.repositories.first() as MavenArtifactRepository
      assertThat(repository.credentials.password).isEqualTo("mock-token-profile")

      verify(exactly = 0) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<CodeArtifactCredentials>()) }
    }

    @Test
    fun `codeartifact extension rejects non-codeartifact urls`() {
      // Given
      val project = ProjectBuilder.builder().build()
      project.plugins.apply("ai.clarity.codeartifact")

      // When/Then
      assertThatThrownBy { project.repositories.codeartifact("https://artifacts.mycompany.com/maven/repository/") }
        .isInstanceOf(java.net.MalformedURLException::class.java)
        .hasMessageContaining("Not a valid CodeArtifact repository URL")

      assertThat(project.repositories).isEmpty()
    }

    @Test
    fun `codeartifact extension accepts an action`() {
      // Given
      val mockResponse = GetAuthorizationTokenResponse.builder()
        .authorizationToken("mock-token")
        .build()

      mockkStatic(TokenFactory::class)
      every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<String>()) } returns mockResponse

      val project = ProjectBuilder.builder().build()
      project.plugins.apply("ai.clarity.codeartifact")

      val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

      // When
      project.repositories.codeartifact(codeArtifactUrl) {
        it.name = "customName"
      }

      // Then
      val repository = project.repositories.first() as MavenArtifactRepository
      assertThat(repository.name).isEqualTo("customName")
      assertThat(repository.url).isEqualTo(URI(codeArtifactUrl))
      assertThat(repository.credentials.username).isEqualTo("aws")
      assertThat(repository.credentials.password).isEqualTo("mock-token")
    }
  }

  @Nested
  inner class MainPluginDispatchTest {

    @Test
    fun `plugin throws for unsupported target types`() {
      // Given
      val plugin = ClarityCodeArtifactGradlePlugin()

      // When/Then
      assertThatThrownBy { plugin.apply("invalid-target") }
        .isInstanceOf(IllegalArgumentException::class.java)
        .hasMessage("This plugin can only be applied to Project or Settings")
    }
  }
}