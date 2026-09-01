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
import groovy.lang.Closure
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import java.net.URI

internal object CodeartifactRepositoryConfigurer {

  fun configure(
    repositories: RepositoryHandler,
    logger: Logger,
    serviceProvider: Provider<CodeArtifactToken>,
    settings: SettingLookup
  ) {
    setupCodeArtifactRepositories(repositories, logger, serviceProvider, settings)
    configRepositories(repositories, logger, serviceProvider, settings)
  }

  private fun setupCodeArtifactRepositories(
    repositories: RepositoryHandler,
    logger: Logger,
    serviceProvider: Provider<CodeArtifactToken>,
    settings: SettingLookup
  ) {
    val ext = (repositories as ExtensionAware).extensions.extraProperties
    // Stash the service provider and the setting lookup so the Kotlin extension function can access them
    ext["codeartifactServiceProvider"] = serviceProvider
    ext["codeartifactSettingLookup"] = settings

    if (!ext.has("codeartifact")) {
      logger.debug("Adding the codeartifact(url, profile|credentials, Closure) method to RepositoryHandler via extraProperties")
      val closure = object : Closure<Any>(repositories, repositories) {

        @Suppress("unused")
        fun doCall(repoUrl: String) = register(repoUrl, null, null, null)

        @Suppress("unused")
        fun doCall(repoUrl: String, profile: String) = register(repoUrl, null, profile, null)

        @Suppress("unused")
        fun doCall(repoUrl: String, profile: String, closure: Closure<*>?) = register(repoUrl, null, profile, closure)

        @Suppress("unused")
        fun doCall(repoUrl: String, credentials: CodeArtifactCredentials) = register(repoUrl, credentials, null, null)

        @Suppress("unused")
        fun doCall(repoUrl: String, credentials: CodeArtifactCredentials, closure: Closure<*>?) =
          register(repoUrl, credentials, null, closure)

        @Suppress("unused")
        fun doCall(repoUrl: String, credentials: Map<*, *>) = doCall(repoUrl, CodeArtifactCredentials.of(credentials))

        @Suppress("unused")
        fun doCall(repoUrl: String, credentials: Map<*, *>, closure: Closure<*>?) =
          doCall(repoUrl, CodeArtifactCredentials.of(credentials), closure)

        private fun register(
          repoUrl: String,
          credentials: CodeArtifactCredentials?,
          profile: String?,
          closure: Closure<*>?
        ) {
          val token = CodeArtifactAuthenticator.getToken(
            serviceProvider.get(), logger, settings, repoUrl, credentials, profile, DEFAULT_PROFILE
          )
          val handler = delegate as RepositoryHandler
          handler.maven { mavenRepo ->
            mavenRepo.url = URI(repoUrl)
            mavenRepo.credentials { creds ->
              creds.username = "aws"
              creds.password = token
            }
            closure?.let {
              it.delegate = mavenRepo
              it.resolveStrategy = DELEGATE_FIRST
              it.call()
            }
          }
        }
      }
      ext["codeartifact"] = closure
    }
  }

  private fun configRepositories(
    repositories: RepositoryHandler,
    logger: Logger,
    serviceProvider: Provider<CodeArtifactToken>,
    settings: SettingLookup
  ) {
    repositories.withType(MavenArtifactRepository::class.java).configureEach { artifactRepository ->
      val repoUri = artifactRepository.url
      if (isCodeArtifactUri(repoUri) && areCredentialsEmpty(artifactRepository)) {
        val token = CodeArtifactAuthenticator.getToken(
          serviceProvider.get(),
          logger,
          settings,
          repoUri.toString(),
          profile = getProfileFromUri(repoUri),
          fallbackProfile = getDefaultProfile(settings)
        )
        artifactRepository.credentials { creds ->
          creds.username = "aws"
          creds.password = token
        }
        artifactRepository.url = removeProfile(repoUri)
      }
    }
  }

  private fun getDefaultProfile(settings: SettingLookup): String? {
    return settings.read("codeartifact.profile", "CODEARTIFACT_PROFILE")
  }

  private fun removeProfile(uri: URI): URI {
    return URIBuilder.of(uri).removeQueryParam("profile").toURI()
  }

  private fun areCredentialsEmpty(mavenRepo: MavenArtifactRepository): Boolean {
    return mavenRepo.credentials.password == null && mavenRepo.credentials.username == null
  }

  // Matched against the host alone, so that a CodeArtifact-looking path cannot pass for an
  // endpoint. Covers the ipv4 host ({domain}-{owner}.d.codeartifact.{region}.amazonaws.com) and the
  // dualstack one ({domain}-{owner}.codeartifact.{region}.on.aws), which has no ".d." segment.
  private val CODEARTIFACT_HOST = "(?i).+\\.codeartifact\\..+\\.(?:amazonaws\\..+|on\\.aws)".toRegex()

  private fun isCodeArtifactUri(uri: URI): Boolean {
    return uri.host?.matches(CODEARTIFACT_HOST) == true
  }

  private fun getProfileFromUri(uri: URI): String? {
    return URIBuilder.of(uri).getQueryParamValue("profile")
  }
}
