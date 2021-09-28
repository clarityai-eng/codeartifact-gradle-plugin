package ai.clarity.codeartifact

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static ai.clarity.codeartifact.ClarityCodeartifactPlugin.*

class CodeArtifactRepositoriesPlugin implements Plugin<Gradle> {
    static Logger logger = LoggerFactory.getLogger(CodeArtifactRepositoriesPlugin)

    @Override
    void apply(Gradle gradle) {
        def tokenProvider = codeArtifactTokenProviderForGradle(gradle)
        gradle.settingsEvaluated { Settings settings ->
            def pluginRepositories = settings.pluginManagement.repositories
            logger.info("settingsEvaluated() block in {} (settings:{}, pluginRepositories:{})", CodeArtifactRepositoriesPlugin, settings, pluginRepositories.asMap)
            configRepositories(logger, pluginRepositories, tokenProvider)
        }
        gradle.allprojects { Project project ->
            logger.info("allprojects() block in {} (project: {})", CodeArtifactRepositoriesPlugin,  project)
            setupCodeartifactRepositoriesByUrl(project, tokenProvider)
        }
    }
}
