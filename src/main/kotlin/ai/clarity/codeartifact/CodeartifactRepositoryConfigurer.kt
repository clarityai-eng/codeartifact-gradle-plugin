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
    serviceProvider: Provider<CodeArtifactToken>
  ) {
    setupCodeArtifactRepositories(repositories, logger, serviceProvider)
    configRepositories(repositories, logger, serviceProvider)
  }

  private fun setupCodeArtifactRepositories(
    repositories: RepositoryHandler,
    logger: Logger,
    serviceProvider: Provider<CodeArtifactToken>
  ) {
    val ext = (repositories as ExtensionAware).extensions.extraProperties
    // Stash the service provider so the Kotlin extension function can access it
    ext["codeartifactServiceProvider"] = serviceProvider

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
            serviceProvider.get(), logger, repoUrl, credentials, profile, DEFAULT_PROFILE
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
    serviceProvider: Provider<CodeArtifactToken>
  ) {
    repositories.withType(MavenArtifactRepository::class.java).configureEach { artifactRepository ->
      val repoUri = artifactRepository.url
      if (isCodeArtifactUri(repoUri) && areCredentialsEmpty(artifactRepository)) {
        val token = CodeArtifactAuthenticator.getToken(
          serviceProvider.get(),
          logger,
          repoUri.toString(),
          profile = getProfileFromUri(repoUri),
          fallbackProfile = getDefaultProfile()
        )
        artifactRepository.credentials { creds ->
          creds.username = "aws"
          creds.password = token
        }
        artifactRepository.url = removeProfile(repoUri)
      }
    }
  }

  private fun getDefaultProfile(): String? {
    return System.getProperty("codeartifact.profile") ?: System.getenv("CODEARTIFACT_PROFILE")
  }

  private fun removeProfile(uri: URI): URI {
    return URIBuilder.of(uri).removeQueryParam("profile").toURI()
  }

  private fun areCredentialsEmpty(mavenRepo: MavenArtifactRepository): Boolean {
    return mavenRepo.credentials.password == null && mavenRepo.credentials.username == null
  }

  private fun isCodeArtifactUri(uri: URI): Boolean {
    return uri.toString().matches("(?i).+\\.codeartifact\\..+\\.amazonaws\\..+".toRegex())
  }

  private fun getProfileFromUri(uri: URI): String? {
    return URIBuilder.of(uri).getQueryParamValue("profile")
  }
}
