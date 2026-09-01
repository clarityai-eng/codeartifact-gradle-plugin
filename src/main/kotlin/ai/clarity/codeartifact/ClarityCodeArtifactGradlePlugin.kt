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

import ai.clarity.codeartifact.CodeArtifactAuthenticator.DEFAULT_PROFILE
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import java.net.URI

@Suppress("unused")
class ClarityCodeArtifactGradlePlugin : Plugin<Any> {

  private val projectPlugin = CodeArtifactProjectPlugin()
  private val settingsPlugin = CodeArtifactSettingsPlugin()

  override fun apply(target: Any) {
    when (target) {
      is Project -> projectPlugin.apply(target)
      is Settings -> settingsPlugin.apply(target)
      else -> throw IllegalArgumentException("This plugin can only be applied to Project or Settings")
    }
  }
}

/**
 * Declares a CodeArtifact repository authenticated with an AWS profile.
 *
 * When [profile] is omitted the repository falls back to the service account credentials configured for the build, and
 * to the `default` profile when there is none.
 */
// Kotlin DSL extension
@Suppress("unused")
fun RepositoryHandler.codeartifact(
  repoUrl: String,
  profile: String? = null,
  action: Action<in MavenArtifactRepository>? = null
) = codeartifactRepository(repoUrl, null, profile, action)

/**
 * Declares a CodeArtifact repository authenticated with the static credentials of a service account, which take
 * precedence over any profile configured for the build.
 */
@Suppress("unused")
fun RepositoryHandler.codeartifact(
  repoUrl: String,
  credentials: CodeArtifactCredentials,
  action: Action<in MavenArtifactRepository>? = null
) = codeartifactRepository(repoUrl, credentials, null, action)

private fun RepositoryHandler.codeartifactRepository(
  repoUrl: String,
  credentials: CodeArtifactCredentials?,
  profile: String?,
  action: Action<in MavenArtifactRepository>?
) {
  val logger = Logging.getLogger(RepositoryHandler::class.java)
  val ext = (this as ExtensionAware).extensions.extraProperties

  @Suppress("UNCHECKED_CAST")
  val serviceProvider = ext["codeartifactServiceProvider"] as Provider<CodeArtifactToken>
  val settings = ext["codeartifactSettingLookup"] as SettingLookup
  val token = CodeArtifactAuthenticator.getToken(
    serviceProvider.get(), logger, settings, repoUrl, credentials, profile, DEFAULT_PROFILE
  )
  maven { mavenRepo ->
    mavenRepo.url = URI(repoUrl)
    mavenRepo.credentials { creds ->
      creds.username = "aws"
      creds.password = token
    }
    action?.execute(mavenRepo)
  }
}
