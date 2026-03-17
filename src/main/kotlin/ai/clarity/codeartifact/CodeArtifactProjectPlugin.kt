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
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension

internal class CodeArtifactProjectPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    val serviceProvider = project.gradle.sharedServices.registerIfAbsent(
      "codeartifact-token",
      CodeArtifactToken::class.java
    ) {}

    CodeartifactRepositoryConfigurer.configure(project.repositories, project.logger, serviceProvider)

    project.plugins.withId("maven-publish") {
      val publishing = project.extensions.findByType(PublishingExtension::class.java)
      publishing?.repositories?.let {
        CodeartifactRepositoryConfigurer.configure(it, project.logger, serviceProvider)
      }
    }
  }
}
