/*
 * Copyright 2020-2021 original authors
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

import groovy.transform.TypeChecked
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static ai.clarity.codeartifact.ClarityCodeartifactPlugin.codeArtifactTokenProviderForGradle

@TypeChecked
class CodeArtifactPluginRepositoriesPlugin implements Plugin<Settings> {
    static Logger logger = LoggerFactory.getLogger(CodeArtifactPluginRepositoriesPlugin)

    void apply(Settings settings) {
        def gradle = settings.getGradle()
        def tokenProvider = codeArtifactTokenProviderForGradle(gradle)
        def pluginRepositories = settings.pluginManagement.repositories
        ClarityCodeartifactPlugin.configRepositories(logger, pluginRepositories, tokenProvider)
    }
}