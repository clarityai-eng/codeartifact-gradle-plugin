package ai.clarity.codeartifact

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.codeartifact.model.GetAuthorizationTokenResponse
import java.net.URI

class CodeArtifactSettingsPluginTest {

  @AfterEach
  fun tearDown() {
    unmockkAll()
    System.clearProperty("codeartifact.accessKeyId")
    System.clearProperty("codeartifact.secretAccessKey")
  }

  @Test
  fun `settings plugin configures pluginManagement repositories for codeartifact URLs`() {
    // Given
    val mockResponse = GetAuthorizationTokenResponse.builder()
      .authorizationToken("mock-settings-token")
      .build()

    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<String>()) } returns mockResponse

    val settings = mockk<Settings>(relaxed = true)
    val gradle = mockk<Gradle>(relaxed = true)
    val buildServiceRegistry = mockk<BuildServiceRegistry>(relaxed = true)
    val serviceProvider = mockk<Provider<CodeArtifactToken>>(relaxed = true)
    val codeArtifactToken = CodeArtifactToken()

    every { settings.gradle } returns gradle
    every { gradle.sharedServices } returns buildServiceRegistry
    every {
      buildServiceRegistry.registerIfAbsent(
        any(),
        any<Class<CodeArtifactToken>>(),
        any()
      )
    } returns serviceProvider
    every { serviceProvider.get() } returns codeArtifactToken

    // Use a real project's repository handler for pluginManagement and dependencyResolutionManagement
    val pluginManagementProject = ProjectBuilder.builder().build()
    val dependencyResolutionProject = ProjectBuilder.builder().build()

    val pluginManagement = mockk<org.gradle.plugin.management.PluginManagementSpec>(relaxed = true)
    val dependencyResolutionManagement =
      mockk<org.gradle.api.initialization.resolve.DependencyResolutionManagement>(relaxed = true)

    every { settings.pluginManagement } returns pluginManagement
    every { pluginManagement.repositories } returns pluginManagementProject.repositories
    every { settings.dependencyResolutionManagement } returns dependencyResolutionManagement
    // A real ProviderFactory, so the plugin reads the settings the way a build does
    every { settings.providers } returns pluginManagementProject.providers
    every { dependencyResolutionManagement.repositories } returns dependencyResolutionProject.repositories

    val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

    // When
    val plugin = CodeArtifactSettingsPlugin()
    plugin.apply(settings)

    pluginManagementProject.repositories.maven { repo ->
      repo.url = URI(codeArtifactUrl)
    }

    val repository = pluginManagementProject.repositories.first() as MavenArtifactRepository

    // Then
    assertThat(repository.credentials.username).isEqualTo("aws")
    assertThat(repository.credentials.password).isEqualTo("mock-settings-token")

    verify(exactly = 1) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<String>()) }
  }

  @Test
  fun `settings plugin configures dependencyResolutionManagement repositories for codeartifact URLs`() {
    // Given
    val mockResponse = GetAuthorizationTokenResponse.builder()
      .authorizationToken("mock-dep-token")
      .build()

    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<String>()) } returns mockResponse

    val settings = mockk<Settings>(relaxed = true)
    val gradle = mockk<Gradle>(relaxed = true)
    val buildServiceRegistry = mockk<BuildServiceRegistry>(relaxed = true)
    val serviceProvider = mockk<Provider<CodeArtifactToken>>(relaxed = true)
    val codeArtifactToken = CodeArtifactToken()

    every { settings.gradle } returns gradle
    every { gradle.sharedServices } returns buildServiceRegistry
    every {
      buildServiceRegistry.registerIfAbsent(
        any(),
        any<Class<CodeArtifactToken>>(),
        any()
      )
    } returns serviceProvider
    every { serviceProvider.get() } returns codeArtifactToken

    val pluginManagementProject = ProjectBuilder.builder().build()
    val dependencyResolutionProject = ProjectBuilder.builder().build()

    val pluginManagement = mockk<org.gradle.plugin.management.PluginManagementSpec>(relaxed = true)
    val dependencyResolutionManagement =
      mockk<org.gradle.api.initialization.resolve.DependencyResolutionManagement>(relaxed = true)

    every { settings.pluginManagement } returns pluginManagement
    every { pluginManagement.repositories } returns pluginManagementProject.repositories
    every { settings.dependencyResolutionManagement } returns dependencyResolutionManagement
    // A real ProviderFactory, so the plugin reads the settings the way a build does
    every { settings.providers } returns pluginManagementProject.providers
    every { dependencyResolutionManagement.repositories } returns dependencyResolutionProject.repositories

    val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.eu-west-1.amazonaws.com/maven/my-repo/"

    // When
    val plugin = CodeArtifactSettingsPlugin()
    plugin.apply(settings)

    dependencyResolutionProject.repositories.maven { repo ->
      repo.url = URI(codeArtifactUrl)
    }

    val repository = dependencyResolutionProject.repositories.first() as MavenArtifactRepository

    // Then
    assertThat(repository.credentials.username).isEqualTo("aws")
    assertThat(repository.credentials.password).isEqualTo("mock-dep-token")

    verify(exactly = 1) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<String>()) }
  }

  @Test
  fun `settings plugin ignores non-codeartifact repositories`() {
    // Given
    mockkStatic(TokenFactory::class)

    val settings = mockk<Settings>(relaxed = true)
    val gradle = mockk<Gradle>(relaxed = true)
    val buildServiceRegistry = mockk<BuildServiceRegistry>(relaxed = true)
    val serviceProvider = mockk<Provider<CodeArtifactToken>>(relaxed = true)

    every { settings.gradle } returns gradle
    every { gradle.sharedServices } returns buildServiceRegistry
    every {
      buildServiceRegistry.registerIfAbsent(
        any(),
        any<Class<CodeArtifactToken>>(),
        any()
      )
    } returns serviceProvider

    val pluginManagementProject = ProjectBuilder.builder().build()
    val dependencyResolutionProject = ProjectBuilder.builder().build()

    val pluginManagement = mockk<org.gradle.plugin.management.PluginManagementSpec>(relaxed = true)
    val dependencyResolutionManagement =
      mockk<org.gradle.api.initialization.resolve.DependencyResolutionManagement>(relaxed = true)

    every { settings.pluginManagement } returns pluginManagement
    every { pluginManagement.repositories } returns pluginManagementProject.repositories
    every { settings.dependencyResolutionManagement } returns dependencyResolutionManagement
    // A real ProviderFactory, so the plugin reads the settings the way a build does
    every { settings.providers } returns pluginManagementProject.providers
    every { dependencyResolutionManagement.repositories } returns dependencyResolutionProject.repositories

    val mavenUrl = "https://repo.maven.apache.org/maven2/"

    // When
    val plugin = CodeArtifactSettingsPlugin()
    plugin.apply(settings)

    pluginManagementProject.repositories.maven { repo ->
      repo.url = URI(mavenUrl)
    }
    dependencyResolutionProject.repositories.maven { repo ->
      repo.url = URI(mavenUrl)
    }

    val pluginRepo = pluginManagementProject.repositories.first() as MavenArtifactRepository
    val depRepo = dependencyResolutionProject.repositories.first() as MavenArtifactRepository

    // Then
    assertThat(pluginRepo.credentials.username).isNull()
    assertThat(pluginRepo.credentials.password).isNull()
    assertThat(depRepo.credentials.username).isNull()
    assertThat(depRepo.credentials.password).isNull()

    verify(exactly = 0) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<String>()) }
  }

  @Test
  fun `settings plugin registers shared build service`() {
    // Given
    val settings = mockk<Settings>(relaxed = true)
    val gradle = mockk<Gradle>(relaxed = true)
    val buildServiceRegistry = mockk<BuildServiceRegistry>(relaxed = true)
    val serviceProvider = mockk<Provider<CodeArtifactToken>>(relaxed = true)

    every { settings.gradle } returns gradle
    every { gradle.sharedServices } returns buildServiceRegistry
    every {
      buildServiceRegistry.registerIfAbsent(
        any(),
        any<Class<CodeArtifactToken>>(),
        any()
      )
    } returns serviceProvider

    val pluginManagementProject = ProjectBuilder.builder().build()
    val dependencyResolutionProject = ProjectBuilder.builder().build()

    val pluginManagement = mockk<org.gradle.plugin.management.PluginManagementSpec>(relaxed = true)
    val dependencyResolutionManagement =
      mockk<org.gradle.api.initialization.resolve.DependencyResolutionManagement>(relaxed = true)

    every { settings.pluginManagement } returns pluginManagement
    every { pluginManagement.repositories } returns pluginManagementProject.repositories
    every { settings.dependencyResolutionManagement } returns dependencyResolutionManagement
    // A real ProviderFactory, so the plugin reads the settings the way a build does
    every { settings.providers } returns pluginManagementProject.providers
    every { dependencyResolutionManagement.repositories } returns dependencyResolutionProject.repositories

    // When
    val plugin = CodeArtifactSettingsPlugin()
    plugin.apply(settings)

    // Then
    verify(exactly = 1) {
      buildServiceRegistry.registerIfAbsent("codeartifact-token", CodeArtifactToken::class.java, any())
    }
  }

  @Test
  fun `settings plugin skips repositories with existing credentials`() {
    // Given
    val mockResponse = GetAuthorizationTokenResponse.builder()
      .authorizationToken("mock-token")
      .build()

    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<String>()) } returns mockResponse

    val settings = mockk<Settings>(relaxed = true)
    val gradle = mockk<Gradle>(relaxed = true)
    val buildServiceRegistry = mockk<BuildServiceRegistry>(relaxed = true)
    val serviceProvider = mockk<Provider<CodeArtifactToken>>(relaxed = true)
    val codeArtifactToken = CodeArtifactToken()

    every { settings.gradle } returns gradle
    every { gradle.sharedServices } returns buildServiceRegistry
    every {
      buildServiceRegistry.registerIfAbsent(
        any(),
        any<Class<CodeArtifactToken>>(),
        any()
      )
    } returns serviceProvider
    every { serviceProvider.get() } returns codeArtifactToken

    val pluginManagementProject = ProjectBuilder.builder().build()
    val dependencyResolutionProject = ProjectBuilder.builder().build()

    val pluginManagement = mockk<org.gradle.plugin.management.PluginManagementSpec>(relaxed = true)
    val dependencyResolutionManagement =
      mockk<org.gradle.api.initialization.resolve.DependencyResolutionManagement>(relaxed = true)

    every { settings.pluginManagement } returns pluginManagement
    every { pluginManagement.repositories } returns pluginManagementProject.repositories
    every { settings.dependencyResolutionManagement } returns dependencyResolutionManagement
    // A real ProviderFactory, so the plugin reads the settings the way a build does
    every { settings.providers } returns pluginManagementProject.providers
    every { dependencyResolutionManagement.repositories } returns dependencyResolutionProject.repositories

    val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

    // When
    val plugin = CodeArtifactSettingsPlugin()
    plugin.apply(settings)

    pluginManagementProject.repositories.maven { repo ->
      repo.url = URI(codeArtifactUrl)
      repo.credentials { creds ->
        creds.username = "existing-user"
        creds.password = "existing-pass"
      }
    }

    val repository = pluginManagementProject.repositories.first() as MavenArtifactRepository

    // Then - credentials should remain unchanged
    assertThat(repository.credentials.username).isEqualTo("existing-user")
    assertThat(repository.credentials.password).isEqualTo("existing-pass")
    verify(exactly = 0) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<String>()) }
  }

  @Test
  fun `settings plugin uses the service credentials of the build`() {
    // Given
    System.setProperty("codeartifact.accessKeyId", "AKIAIOSFODNN7EXAMPLE")
    System.setProperty("codeartifact.secretAccessKey", "service-user-secret")

    val credentials = CodeArtifactCredentials.of("AKIAIOSFODNN7EXAMPLE", "service-user-secret")

    mockkStatic(TokenFactory::class)
    every { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), credentials) } returns
      GetAuthorizationTokenResponse.builder().authorizationToken("mock-service-user-token").build()

    val settings = mockk<Settings>(relaxed = true)
    val gradle = mockk<Gradle>(relaxed = true)
    val buildServiceRegistry = mockk<BuildServiceRegistry>(relaxed = true)
    val serviceProvider = mockk<Provider<CodeArtifactToken>>(relaxed = true)
    val codeArtifactToken = CodeArtifactToken()

    every { settings.gradle } returns gradle
    every { gradle.sharedServices } returns buildServiceRegistry
    every {
      buildServiceRegistry.registerIfAbsent(
        any(),
        any<Class<CodeArtifactToken>>(),
        any()
      )
    } returns serviceProvider
    every { serviceProvider.get() } returns codeArtifactToken

    val pluginManagementProject = ProjectBuilder.builder().build()
    val dependencyResolutionProject = ProjectBuilder.builder().build()

    val pluginManagement = mockk<org.gradle.plugin.management.PluginManagementSpec>(relaxed = true)
    val dependencyResolutionManagement =
      mockk<org.gradle.api.initialization.resolve.DependencyResolutionManagement>(relaxed = true)

    every { settings.pluginManagement } returns pluginManagement
    every { pluginManagement.repositories } returns pluginManagementProject.repositories
    every { settings.dependencyResolutionManagement } returns dependencyResolutionManagement
    // A real ProviderFactory, so the plugin reads the settings the way a build does
    every { settings.providers } returns pluginManagementProject.providers
    every { dependencyResolutionManagement.repositories } returns dependencyResolutionProject.repositories

    val codeArtifactUrl = "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"

    // When
    val plugin = CodeArtifactSettingsPlugin()
    plugin.apply(settings)

    pluginManagementProject.repositories.maven { repo ->
      repo.url = URI(codeArtifactUrl)
    }

    val repository = pluginManagementProject.repositories.first() as MavenArtifactRepository

    // Then
    assertThat(repository.credentials.username).isEqualTo("aws")
    assertThat(repository.credentials.password).isEqualTo("mock-service-user-token")

    verify(exactly = 1) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), credentials) }
    verify(exactly = 0) { TokenFactory.getAuthorizationToken(any<CodeArtifactUrl>(), any<String>()) }
  }

}
