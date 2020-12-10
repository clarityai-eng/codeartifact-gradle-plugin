package ai.clarity.codeartifact

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class ClarityCodeartifactPlugin implements Plugin<Project> {
    void apply(Project project) {
        Provider<CodeartifactToken> serviceProvider = project
                .getGradle()
                .getSharedServices()
                .registerIfAbsent("codeartifact-token", CodeartifactToken.class, {})

        setupCodeartifactRepositories(project, serviceProvider)
    }

    static void setupCodeartifactRepositories(Project project, Provider<CodeartifactToken> serviceProvider) {
        if (!project.repositories.metaClass.respondsTo(project.repositories, 'codeartifact', String, String, Object)) {
            project.logger.debug 'Adding codeartifact(String,String?,Closure?) method to project RepositoryHandler'
            project.repositories.metaClass.codeartifact = { String repoUrl, String profile = 'default', def closure = null ->
                project.logger.debug "Getting token for $repoUrl in profile $profile"
                def token = serviceProvider.get().getToken(repoUrl, profile)
                delegate.maven {
                    url repoUrl
                    credentials {
                        username 'aws'
                        password token
                    }
                }
                if (closure) {
                    closure.delegate = delegate
                    closure()
                }
            }
        }
    }
}