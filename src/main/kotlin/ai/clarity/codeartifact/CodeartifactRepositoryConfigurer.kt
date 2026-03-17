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
    setupCodeartifactRepositories(repositories, logger, serviceProvider)
    configRepositories(repositories, logger, serviceProvider)
  }

  private fun setupCodeartifactRepositories(
    repositories: RepositoryHandler,
    logger: Logger,
    serviceProvider: Provider<CodeArtifactToken>
  ) {
    val ext = (repositories as ExtensionAware).extensions.extraProperties
    // Stash the service provider so the Kotlin extension function can access it
    ext.set("codeartifactServiceProvider", serviceProvider)

    if (!ext.has("codeartifact")) {
      logger.debug("Adding codeartifact(String, String, Closure) method to RepositoryHandler via extraProperties")
      val closure = object : Closure<Any>(repositories, repositories) {
        @Suppress("unused")
        fun doCall(repoUrl: String, profile: String = "default", closure: Closure<*>? = null) {
          logger.info("Getting token for $repoUrl in profile $profile")
          val token = serviceProvider.get().getToken(repoUrl, profile)
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

        @Suppress("unused")
        fun doCall(repoUrl: String) {
          doCall(repoUrl, "default", null)
        }

        @Suppress("unused")
        fun doCall(repoUrl: String, profile: String) {
          doCall(repoUrl, profile, null)
        }
      }
      ext.set("codeartifact", closure)
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
        val profile = getProfileFromUri(repoUri, getDefaultProfile())
        logger.info("Getting token for {} in profile {}", repoUri, profile)

        val token = serviceProvider.get().getToken(repoUri, profile)
        artifactRepository.credentials { creds ->
          creds.username = "aws"
          creds.password = token
        }
        artifactRepository.url = removeProfile(repoUri)
      }
    }
  }

  private fun getDefaultProfile(): String {
    return System.getProperty("codeartifact.profile") ?: System.getenv("CODEARTIFACT_PROFILE") ?: "default"
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

  private fun getProfileFromUri(uri: URI, defaultValue: String): String {
    return URIBuilder.of(uri).getQueryParamValue("profile") ?: defaultValue
  }
}
