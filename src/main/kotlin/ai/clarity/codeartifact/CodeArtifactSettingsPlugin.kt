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

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logging

internal class CodeArtifactSettingsPlugin : Plugin<Settings> {

  override fun apply(settings: Settings) {
    val serviceProvider = settings.gradle.sharedServices.registerIfAbsent(
      "codeartifact-token",
      CodeArtifactToken::class.java
    ) {}

    val logger = Logging.getLogger(Settings::class.java)
    val lookup = SettingLookup.of(settings.providers, logger)

    CodeartifactRepositoryConfigurer.configure(settings.pluginManagement.repositories, logger, serviceProvider, lookup)
    CodeartifactRepositoryConfigurer.configure(
      settings.dependencyResolutionManagement.repositories, logger, serviceProvider, lookup
    )
  }
}
