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

import org.gradle.api.provider.ProviderFactory
import java.io.File

/**
 * Obtains profile tokens through [CodeArtifactTokenSource], keeping them in [cacheDirectory] between builds.
 */
internal class ProfileTokens(private val providers: ProviderFactory, private val cacheDirectory: File) {

  fun obtain(repoUrl: String, profile: String?): String =
    providers.of(CodeArtifactTokenSource::class.java) { spec ->
      spec.parameters { parameters ->
        parameters.repositoryUrl.set(repoUrl)
        parameters.profile.set(profile)
        parameters.cacheDirectory.set(cacheDirectory)
      }
    }.get()

  companion object {
    fun of(providers: ProviderFactory, gradleUserHome: File) =
      ProfileTokens(providers, File(gradleUserHome, "caches/codeartifact-gradle-plugin/tokens"))
  }
}
