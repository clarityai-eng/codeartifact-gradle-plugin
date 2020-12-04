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

    static GetAuthorizationTokenResponse getAuthorizationToken(url, String profileName) {
        CodeArtifactUrl codeArtifactUrl = CodeArtifactUrl.of(url)

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
                def token = getAuthorizationToken(repoUrl, profile)
                delegate.maven {
                    url repoUrl
                    credentials {
                        username 'aws'
                        password token.authorizationToken()
                    }
                }
                if (closure) {
                    closure.delegate = del
                    closure()
                }
            }
        }

        return true
    }

    static class CodeArtifactUrl {

        private final URL url
        private final String artifactDomain
        private final String artifactOwner
        private final String region
        private final String path

        CodeArtifactUrl(URL url) {
            this.url = url
            String[] domainLevels = this.url.getHost().split("\\.")
            path = url.getPath()
            artifactDomain = domainLevels[0].substring(0, domainLevels[0].lastIndexOf("-"))
            artifactOwner = domainLevels[0].substring(domainLevels[0].lastIndexOf("-") + 1)
            region = domainLevels[domainLevels.length - 3]
        }

        CodeArtifactUrl(String artifactDomain, String artifactOwner, String region, String path) {
            this.artifactDomain = artifactDomain
            this.artifactOwner = artifactOwner
            this.region = region
            this.path = normalizePath(path)
            this.url = new URL(String.format("https://%s-%s.d.codeartifact.%s.amazonaws.com/%s", artifactDomain, artifactOwner, region, path))
        }

        static CodeArtifactUrl of(String url) throws MalformedURLException {
            return of(new URL(url))
        }

        static CodeArtifactUrl of(String artifactDomain, String artifactOwner, String region, String path) throws MalformedURLException {
            return new CodeArtifactUrl(artifactDomain, artifactOwner, region, path)
        }

        static CodeArtifactUrl of(URL url) {
            return new CodeArtifactUrl(url)
        }

        private static String normalizePath(String path) {
            if (path.startsWith("/")) {
                path = path.substring(1)
            }
            if (!path.endsWith("/")) {
                path += "/"
            }

            return path
        }

        URL getUrl() {
            return url
        }

        String getArtifactDomain() {
            return artifactDomain
        }

        String getArtifactOwner() {
            return artifactOwner
        }

        String getRegion() {
            return region
        }

        String getPath() {
            return path
        }
    }
}