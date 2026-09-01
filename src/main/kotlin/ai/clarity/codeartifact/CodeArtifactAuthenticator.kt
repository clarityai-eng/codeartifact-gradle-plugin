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

import org.gradle.api.logging.Logger

/**
 * Decides how a single repository authenticates against CodeArtifact and returns its authorization token.
 */
internal object CodeArtifactAuthenticator {

  const val DEFAULT_PROFILE = "default"

  /**
   * Resolves the authentication for [repoUrl] and returns the token, giving precedence to the configuration that is
   * closest to the repository:
   *
   *  1. [credentials] declared on the repository itself
   *  2. [profile] declared on the repository itself
   *  3. the service account credentials shared by the build, read from [settings] by
   *     [CodeArtifactCredentialsResolver]
   *  4. [fallbackProfile], which may be `null` to let the AWS SDK resolve the credentials on its own
   */
  fun getToken(
    tokenService: CodeArtifactToken,
    logger: Logger,
    settings: SettingLookup,
    repoUrl: String,
    credentials: CodeArtifactCredentials? = null,
    profile: String? = null,
    fallbackProfile: String? = null
  ): String {
    val serviceCredentials = credentials ?: if (profile == null) CodeArtifactCredentialsResolver.resolve(settings) else null
    if (serviceCredentials != null) {
      logger.info("Getting token for {} with the service credentials {}", repoUrl, serviceCredentials.maskedAccessKeyId)
      return tokenService.getToken(repoUrl, serviceCredentials)
    }

    val resolvedProfile = profile ?: fallbackProfile
    logger.info("Getting token for {} in profile {}", repoUrl, resolvedProfile)

    return tokenService.getToken(repoUrl, resolvedProfile)
  }
}
