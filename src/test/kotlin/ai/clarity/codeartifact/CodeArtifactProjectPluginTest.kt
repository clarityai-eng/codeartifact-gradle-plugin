package ai.clarity.codeartifact

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.codeartifact.model.GetAuthorizationTokenResponse
import java.net.URI

class CodeArtifactProjectPluginTest {


  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `plugin configures credentials for codeartifact repository URLs`() {
    // Given
    val mockResponse = GetAuthorizationTokenResponse.builder()
      .authorizationToken("mock-token")
      .build()

    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any()) } returns mockResponse

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

    verify(exactly = 1) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any()) }
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

}