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

package ai.clarity.codeartifact;

import java.net.MalformedURLException;
import java.time.Clock;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

/**
 * Obtains the authorization token of a CodeArtifact repository for an AWS profile.
 *
 * <p>Fetching the token inside a {@link ValueSource} is what keeps it configuration cache compatible: the AWS SDK may
 * start a {@code credential_process}, which Gradle only allows here. Gradle obtains the value again on every build to
 * check the cache entry, so the token is kept in a {@link TokenCache} under {@link Parameters#getCacheDirectory()}:
 * while it is valid the value does not change and the entry is reused, and once it is refreshed the entry is
 * invalidated rather than replaying an expired token.
 *
 * <p>Gradle instantiates it through {@code providers.of}; there is no reason to construct one directly.
 */
public abstract class CodeArtifactTokenSource implements ValueSource<String, CodeArtifactTokenSource.Parameters> {

  // Inputs the AWS SDK uses to choose the identity when no profile is given, so that changing any of them between
  // builds fetches a token for the new identity instead of reusing the cached one
  private static final String[] IDENTITY_ENVIRONMENT = {
    "AWS_PROFILE", "AWS_ACCESS_KEY_ID", "AWS_CONFIG_FILE", "AWS_SHARED_CREDENTIALS_FILE",
    "AWS_ENDPOINT_URL", "AWS_ENDPOINT_URL_CODEARTIFACT"
  };

  /**
   * The repository and profile to obtain the token for.
   */
  public interface Parameters extends ValueSourceParameters {

    /**
     * The CodeArtifact repository url.
     *
     * @return the repository url
     */
    Property<String> getRepositoryUrl();

    /**
     * The AWS profile to authenticate with; absent to let the AWS SDK resolve the credentials on its own.
     *
     * @return the profile name
     */
    Property<String> getProfile();

    /**
     * The directory that keeps tokens between builds.
     *
     * @return the cache directory
     */
    DirectoryProperty getCacheDirectory();
  }

  /**
   * Creates the value source. Called by Gradle.
   */
  public CodeArtifactTokenSource() {
  }

  /**
   * Returns the cached token while it is still valid, and otherwise fetches and caches a new one.
   *
   * @return the authorization token
   */
  @Override
  public String obtain() {
    Parameters parameters = getParameters();
    String repositoryUrl = parameters.getRepositoryUrl().get();
    String profile = parameters.getProfile().getOrNull();
    CodeArtifactUrl codeArtifactUrl;
    try {
      codeArtifactUrl = CodeArtifactUrl.of(repositoryUrl);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
    TokenCache cache = new TokenCache(parameters.getCacheDirectory().get().getAsFile().toPath(), Clock.systemUTC());
    return cache.get(cacheKey(repositoryUrl, profile),
      () -> TokenFactory.getAuthorizationToken(codeArtifactUrl, profile));
  }

  static String cacheKey(String repositoryUrl, String profile) {
    String environment = Stream.of(IDENTITY_ENVIRONMENT)
      .map(name -> name + "=" + Objects.toString(System.getenv(name), ""))
      .collect(Collectors.joining(","));
    return "profile:" + profile + "@" + repositoryUrl + "|" + environment;
  }
}
