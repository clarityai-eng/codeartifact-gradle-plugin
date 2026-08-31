package ai.clarity.codeartifact

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.publish.PublishingExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.codeartifact.model.GetAuthorizationTokenResponse
import java.net.URI

class CodeArtifactProjectPluginTest {


  @AfterEach
  fun tearDown() {
    unmockkAll()
    System.clearProperty("codeartifact.profile")
    System.clearProperty("codeartifact.accessKeyId")
    System.clearProperty("codeartifact.secretAccessKey")
    System.clearProperty("codeartifact.sessionToken")
  }

  @Test
  fun `plugin configures credentials for codeartifact repository URLs`() {
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
    project.repositories.maven { repo ->
      repo.url = URI(codeArtifactUrl)
    }
    val repository = project.repositories.first() as MavenArtifactRepository

    // Then
    assertThat(repository.credentials.username).isEqualTo("aws")
    assertThat(repository.credentials.password).isEqualTo("mock-token")

    verify(exactly = 1) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<String>()) }
  }

  @Test
  fun `plugin ignores non-codeartifact repositories`() {
    // Given
    val project = ProjectBuilder.builder().build()
    project.plugins.apply("ai.clarity.codeartifact")

    val mavenUrl = "https://repo.maven.apache.org/maven2/"

    // When
    project.repositories.maven { repo ->
      repo.url = URI(mavenUrl)
    }
    val repository = project.repositories.first() as MavenArtifactRepository

    // Then
    assertThat(repository.credentials.username).isNull()
    assertThat(repository.credentials.password).isNull()
  }

  @Test
  fun `plugin uses profile from url query param and removes it from the repository url`() {
    // Given
    val mockResponse = GetAuthorizationTokenResponse.builder()
      .authorizationToken("mock-token-ci")
      .build()

    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "ci-profile") } returns mockResponse

    val project = ProjectBuilder.builder().build()
    project.plugins.apply("ai.clarity.codeartifact")

    val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

    // When
    project.repositories.maven { repo ->
      repo.url = URI("$codeArtifactUrl?profile=ci-profile")
    }
    val repository = project.repositories.first() as MavenArtifactRepository

    // Then
    assertThat(repository.url).isEqualTo(URI(codeArtifactUrl))
    assertThat(repository.credentials.username).isEqualTo("aws")
    assertThat(repository.credentials.password).isEqualTo("mock-token-ci")

    verify(exactly = 1) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "ci-profile") }
  }

  @Test
  fun `plugin uses the codeartifact profile system property as default profile`() {
    // Given
    System.setProperty("codeartifact.profile", "sysprop-profile")

    val mockResponse = GetAuthorizationTokenResponse.builder()
      .authorizationToken("mock-token-sysprop")
      .build()

    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "sysprop-profile") } returns mockResponse

    val project = ProjectBuilder.builder().build()
    project.plugins.apply("ai.clarity.codeartifact")

    val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

    // When
    project.repositories.maven { repo ->
      repo.url = URI(codeArtifactUrl)
    }
    val repository = project.repositories.first() as MavenArtifactRepository

    // Then
    assertThat(repository.credentials.username).isEqualTo("aws")
    assertThat(repository.credentials.password).isEqualTo("mock-token-sysprop")

    verify(exactly = 1) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "sysprop-profile") }
  }

  @Test
  fun `profile from url query param has precedence over the system property`() {
    // Given
    System.setProperty("codeartifact.profile", "sysprop-profile")

    val mockResponse = GetAuthorizationTokenResponse.builder()
      .authorizationToken("mock-token-url")
      .build()

    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "url-profile") } returns mockResponse

    val project = ProjectBuilder.builder().build()
    project.plugins.apply("ai.clarity.codeartifact")

    val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

    // When
    project.repositories.maven { repo ->
      repo.url = URI("$codeArtifactUrl?profile=url-profile")
    }
    val repository = project.repositories.first() as MavenArtifactRepository

    // Then
    assertThat(repository.credentials.password).isEqualTo("mock-token-url")
    verify(exactly = 1) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "url-profile") }
    verify(exactly = 0) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "sysprop-profile") }
  }

  @Test
  fun `plugin configures credentials for publishing repositories when maven-publish is applied`() {
    // Given
    val mockResponse = GetAuthorizationTokenResponse.builder()
      .authorizationToken("mock-token-publish")
      .build()

    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<String>()) } returns mockResponse

    val project = ProjectBuilder.builder().build()
    project.plugins.apply("ai.clarity.codeartifact")
    project.plugins.apply("maven-publish")

    val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

    // When
    val publishing = project.extensions.getByType(PublishingExtension::class.java)
    publishing.repositories.maven { repo ->
      repo.url = URI(codeArtifactUrl)
    }
    val repository = publishing.repositories.first() as MavenArtifactRepository

    // Then
    assertThat(repository.credentials.username).isEqualTo("aws")
    assertThat(repository.credentials.password).isEqualTo("mock-token-publish")

    verify(exactly = 1) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<String>()) }
  }

  @Test
  fun `plugin skips codeartifact repositories with existing credentials`() {
    // Given
    mockkStatic(TokenFactory::class)

    val project = ProjectBuilder.builder().build()
    project.plugins.apply("ai.clarity.codeartifact")

    val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

    // When
    project.repositories.maven { repo ->
      repo.url = URI(codeArtifactUrl)
      repo.credentials { creds ->
        creds.username = "existing-user"
        creds.password = "existing-pass"
      }
    }
    val repository = project.repositories.first() as MavenArtifactRepository

    // Then
    assertThat(repository.credentials.username).isEqualTo("existing-user")
    assertThat(repository.credentials.password).isEqualTo("existing-pass")

    verify(exactly = 0) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<String>()) }
  }

  @Test
  fun `plugin detects codeartifact urls case-insensitively`() {
    // Given
    val mockResponse = GetAuthorizationTokenResponse.builder()
      .authorizationToken("mock-token-case")
      .build()

    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<String>()) } returns mockResponse

    val project = ProjectBuilder.builder().build()
    project.plugins.apply("ai.clarity.codeartifact")

    val codeArtifactUrl = "https://my-domain-111122223333.d.CodeArtifact.us-west-2.amazonaws.com/maven/my-repo/"

    // When
    project.repositories.maven { repo ->
      repo.url = URI(codeArtifactUrl)
    }
    val repository = project.repositories.first() as MavenArtifactRepository

    // Then
    assertThat(repository.credentials.username).isEqualTo("aws")
    assertThat(repository.credentials.password).isEqualTo("mock-token-case")
  }

  @Test
  fun `plugin uses the service credentials from the system properties`() {
    // Given
    System.setProperty("codeartifact.accessKeyId", "AKIAIOSFODNN7EXAMPLE")
    System.setProperty("codeartifact.secretAccessKey", "service-user-secret")

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
    project.repositories.maven { repo ->
      repo.url = URI(codeArtifactUrl)
    }
    val repository = project.repositories.first() as MavenArtifactRepository

    // Then
    assertThat(repository.credentials.username).isEqualTo("aws")
    assertThat(repository.credentials.password).isEqualTo("mock-token-service-user")

    verify(exactly = 1) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), credentials) }
    verify(exactly = 0) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<String>()) }
  }

  @Test
  fun `service credentials carry the session token when configured`() {
    // Given
    System.setProperty("codeartifact.accessKeyId", "AKIAIOSFODNN7EXAMPLE")
    System.setProperty("codeartifact.secretAccessKey", "service-user-secret")
    System.setProperty("codeartifact.sessionToken", "service-user-session-token")

    val credentials =
      CodeArtifactCredentials.of("AKIAIOSFODNN7EXAMPLE", "service-user-secret", "service-user-session-token")

    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), credentials) } returns
      GetAuthorizationTokenResponse.builder().authorizationToken("mock-token-temporary").build()

    val project = ProjectBuilder.builder().build()
    project.plugins.apply("ai.clarity.codeartifact")

    val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

    // When
    project.repositories.maven { repo ->
      repo.url = URI(codeArtifactUrl)
    }
    val repository = project.repositories.first() as MavenArtifactRepository

    // Then
    assertThat(repository.credentials.password).isEqualTo("mock-token-temporary")
    verify(exactly = 1) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), credentials) }
  }

  @Test
  fun `service credentials take precedence over the profile system property`() {
    // Given
    System.setProperty("codeartifact.profile", "sysprop-profile")
    System.setProperty("codeartifact.accessKeyId", "AKIAIOSFODNN7EXAMPLE")
    System.setProperty("codeartifact.secretAccessKey", "service-user-secret")

    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<CodeArtifactCredentials>()) } returns
      GetAuthorizationTokenResponse.builder().authorizationToken("mock-token-service-user").build()

    val project = ProjectBuilder.builder().build()
    project.plugins.apply("ai.clarity.codeartifact")

    val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

    // When
    project.repositories.maven { repo ->
      repo.url = URI(codeArtifactUrl)
    }
    val repository = project.repositories.first() as MavenArtifactRepository

    // Then
    assertThat(repository.credentials.password).isEqualTo("mock-token-service-user")
    verify(exactly = 0) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "sysprop-profile") }
  }

  @Test
  fun `profile from url query param has precedence over the service credentials`() {
    // Given
    System.setProperty("codeartifact.accessKeyId", "AKIAIOSFODNN7EXAMPLE")
    System.setProperty("codeartifact.secretAccessKey", "service-user-secret")

    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "url-profile") } returns
      GetAuthorizationTokenResponse.builder().authorizationToken("mock-token-url").build()

    val project = ProjectBuilder.builder().build()
    project.plugins.apply("ai.clarity.codeartifact")

    val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

    // When
    project.repositories.maven { repo ->
      repo.url = URI("$codeArtifactUrl?profile=url-profile")
    }
    val repository = project.repositories.first() as MavenArtifactRepository

    // Then
    assertThat(repository.credentials.password).isEqualTo("mock-token-url")
    verify(exactly = 1) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), "url-profile") }
    verify(exactly = 0) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<CodeArtifactCredentials>()) }
  }

  @Test
  fun `plugin configures publishing repositories with the service credentials`() {
    // Given
    System.setProperty("codeartifact.accessKeyId", "AKIAIOSFODNN7EXAMPLE")
    System.setProperty("codeartifact.secretAccessKey", "service-user-secret")

    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<CodeArtifactCredentials>()) } returns
      GetAuthorizationTokenResponse.builder().authorizationToken("mock-token-publish").build()

    val project = ProjectBuilder.builder().build()
    project.plugins.apply("ai.clarity.codeartifact")
    project.plugins.apply("maven-publish")

    val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

    // When
    val publishing = project.extensions.getByType(PublishingExtension::class.java)
    publishing.repositories.maven { repo ->
      repo.url = URI(codeArtifactUrl)
    }
    val repository = publishing.repositories.first() as MavenArtifactRepository

    // Then
    assertThat(repository.credentials.username).isEqualTo("aws")
    assertThat(repository.credentials.password).isEqualTo("mock-token-publish")
  }

  @Test
  fun `incomplete service credentials fail with a descriptive error`() {
    // Given
    System.setProperty("codeartifact.accessKeyId", "AKIAIOSFODNN7EXAMPLE")

    val project = ProjectBuilder.builder().build()
    project.plugins.apply("ai.clarity.codeartifact")

    val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

    // When/Then
    assertThatThrownBy {
      project.repositories.maven { repo ->
        repo.url = URI(codeArtifactUrl)
      }
    }
      .isInstanceOf(InvalidUserDataException::class.java)
      .hasMessageContaining("codeartifact.secretAccessKey")
  }
}
