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
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.UrlArtifactRepository
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.slf4j.Logger

class ClarityCodeartifactPlugin implements Plugin<Project> {
    private Logger logger

    void apply(Project project) {
        this.logger = project.logger
        def gradle = project.getGradle()
        def tokenProvider = codeArtifactTokenProviderForGradle(gradle)
        setupCodeartifactRepositories(project, tokenProvider)
        setupCodeartifactRepositoriesByUrl(project, tokenProvider)
    }

    static Provider<CodeartifactToken> codeArtifactTokenProviderForGradle(Gradle gradle) {
        Provider<CodeartifactToken> serviceProvider = gradle
                .getSharedServices()
                .registerIfAbsent('codeartifact-token', CodeartifactToken.class, {})
        serviceProvider
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

    static void setupCodeartifactRepositoriesByUrl(Project project, Provider<CodeartifactToken> serviceProvider) {
        project.afterEvaluate({ Project p ->
            configRepositories(p.logger, p.repositories, serviceProvider)
            p.plugins.withId('maven-publish', { publishPlugin ->
                p.extensions.configure('publishing', { publishing ->
                    configRepositories(p.logger, publishing.getRepositories(), serviceProvider)
                })
            })
        })
    }

    static void configRepositories(Logger logger, RepositoryHandler repositories, Provider<CodeartifactToken> serviceProvider) {
        logger.info("configRepositories({})", Thread.currentThread().name, repositories.join(","))
        ListIterator it = repositories.listIterator()
        while (it.hasNext()) {
            def artifactRepository = it.next()
            if (artifactRepository instanceof MavenArtifactRepository) {
                MavenArtifactRepository mavenRepo = (MavenArtifactRepository) artifactRepository;
                def repoUri = mavenRepo.getUrl()
                def hasCodeArtifactUri = isCodeArtifactUri(repoUri)
                def hasNoCredentials = areCredentialsEmpty(mavenRepo)
                logger.info("MavenArtifactRepository {} hasCodeArtifactUri:{} hasNoCredentials:{} ", repoUri, hasCodeArtifactUri, hasNoCredentials)
                if (hasCodeArtifactUri && hasNoCredentials) {
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

    private static String getDefaultProfile() {
        return System.getProperty("codeartifact.profile", System.getenv("CODEARTIFACT_PROFILE"));
    }

    private static URI removeProfile(URI uri) {
        return URIBuilder.of(uri).removeQueryParam("profile").toURI();
    }

    private static boolean areCredentialsEmpty(MavenArtifactRepository mavenRepo) {
        return mavenRepo.getCredentials().getPassword() == null && mavenRepo.getCredentials().getUsername() == null
    }

    private static boolean isCodeArtifactUri(URI uri) {
        return uri.toString().matches('(?i).+\\.codeartifact\\..+\\.amazonaws\\..+')
    }

    private static String getProfileFromUri(URI uri, String defaultValue) {
        def value = URIBuilder.of(uri).getQueryParamValue("profile")
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }
}