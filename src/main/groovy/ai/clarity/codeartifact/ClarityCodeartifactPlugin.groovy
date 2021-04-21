package ai.clarity.codeartifact


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Provider

class ClarityCodeartifactPlugin implements Plugin<Project> {
    private Logger logger

    void apply(Project project) {
        this.logger = project.logger;
        Provider<CodeartifactToken> serviceProvider = project
                .getGradle()
                .getSharedServices()
                .registerIfAbsent('codeartifact-token', CodeartifactToken.class, {})
        setupCodeartifactRepositories(project, serviceProvider)
        setupCodeartifactRepositoriesByUrl(project, serviceProvider)
    }

    private void setupCodeartifactRepositories(Project project, Provider<CodeartifactToken> serviceProvider) {
        if (!project.repositories.metaClass.respondsTo(project.repositories, 'codeartifact', String, String, Object)) {
            logger.debug 'Adding codeartifact(String,String?,Closure?) method to project RepositoryHandler'
            project.repositories.metaClass.codeartifact = { String repoUrl, String profile = 'default', def closure = null ->
                logger.debug "Getting token for $repoUrl in profile $profile"
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

    private void setupCodeartifactRepositoriesByUrl(Project project, Provider<CodeartifactToken> serviceProvider) {
        project.afterEvaluate({ p ->
            configRepositories(p.repositories, serviceProvider)
            p.plugins.withId('maven-publish', { publishPlugin ->
                p.extensions.configure('publishing', { publishing ->
                    configRepositories(publishing.getRepositories(), serviceProvider)
                })
            })
        })
    }

    private void configRepositories(RepositoryHandler repositories, Provider<CodeartifactToken> serviceProvider) {
        ListIterator it = repositories.listIterator()
        while (it.hasNext()) {
            def artifactRepository = it.next()
            if (artifactRepository instanceof MavenArtifactRepository) {
                MavenArtifactRepository mavenRepo = (MavenArtifactRepository) artifactRepository;
                URI repoUri = mavenRepo.getUrl()
                if (isCodeArtifactUri(repoUri) && areCredentialsEmpty(mavenRepo)) {
                    String profile = getProfileFromUri(repoUri, getDefaultProfile())
                    logger.info('Getting token for {} in profile {}', repoUri.toString(), profile)
                    String token = serviceProvider.get().getToken(repoUri, profile)
                    mavenRepo.credentials({
                        username 'aws'
                        password token
                    })

                    mavenRepo.setUrl(removeProfile(repoUri))
                }
            }
        }
    }

    private String getDefaultProfile() {
        return System.getProperty("codeartifact.profile", System.getenv("CODEARTIFACT_PROFILE"));
    }

    private URI removeProfile(URI uri) {
        return URIBuilder.of(uri).removeQueryParam("profile").toURI();
    }

    private boolean areCredentialsEmpty(MavenArtifactRepository mavenRepo) {
        return mavenRepo.getCredentials().getPassword() == null && mavenRepo.getCredentials().getUsername() == null
    }

    private boolean isCodeArtifactUri(URI uri) {
        return uri.toString().matches('(?i).+\\.codeartifact\\..+\\.amazonaws\\..+')
    }

    private String getProfileFromUri(URI uri, String defaultValue) {
        def value = URIBuilder.of(uri).getQueryParamValue("profile")
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }
}