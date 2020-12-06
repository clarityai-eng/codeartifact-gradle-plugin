package ai.clarity.codeartifact

import org.gradle.api.Plugin
import org.gradle.api.Project
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.codeartifact.CodeartifactClient
import software.amazon.awssdk.services.codeartifact.model.GetAuthorizationTokenResponse

class ClarityCodeartifactPlugin implements Plugin<Project> {
    void apply(Project project) {
        setupCodeartifactRepositories(project)
    }

    static GetAuthorizationTokenResponse getAuthorizationToken(CodeArtifactUrl codeArtifactUrl, String profileName) {
        CodeartifactClient client = CodeartifactClient.builder()
                .credentialsProvider(ProfileCredentialsProvider.create(profileName))
                .region(Region.of(codeArtifactUrl.getRegion()))
                .build()

        GetAuthorizationTokenResponse result = client.getAuthorizationToken({ req -> req.domain(codeArtifactUrl.getArtifactDomain()).domainOwner(codeArtifactUrl.getArtifactOwner()) })

        return result
    }

    static boolean setupCodeartifactRepositories(Project project) {
        if (!project.repositories.metaClass.respondsTo(project.repositories, 'codeartifact', String, String, Object)) {
            project.logger.debug 'Adding codeartifact(String,String?,Closure?) method to project RepositoryHandler'
            project.repositories.metaClass.codeartifact = { String repoUrl, String profile = 'default', def closure = null ->
                def token = getAuthorizationToken(CodeArtifactUrl.of(repoUrl), profile)
                delegate.maven {
                    url repoUrl
                    credentials {
                        username 'aws'
                        password token.authorizationToken()
                    }
                }
                if (closure) {
                    closure.delegate = delegate
                    closure()
                }
            }
        }

        return true
    }
}