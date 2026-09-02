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
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceParameters.None;

/**
 * Build service that fetches CodeArtifact authorization tokens and shares them across the whole build.
 *
 * <p>A token is requested once per authentication and repository url, and reused from an in-memory cache afterwards.
 * Profile entries and service-credential entries never share a cache slot, and the credentials half of the key is a
 * digest, so no secret is held in clear.
 *
 * <p>Gradle instantiates it through {@code sharedServices.registerIfAbsent}; there is no reason to construct one
 * directly.
 */
public class CodeArtifactToken implements BuildService<None> {

  private final ConcurrentHashMap<String, String> tokensCache = new ConcurrentHashMap<>();

  /**
   * Creates the service. Called by Gradle, which owns the single instance registered for the build.
   */
  public CodeArtifactToken() {
  }

  /**
   * Returns the authorization token for {@code uri}, authenticating with an AWS profile.
   *
   * @param uri     the CodeArtifact repository url
   * @param profile the AWS profile to authenticate with, or {@code null} to let the AWS SDK resolve the credentials
   * @return the authorization token, from the cache when this authentication and url were already resolved
   * @throws MalformedURLException when {@code uri} is not a valid CodeArtifact repository url
   */
  public String getToken(URI uri, String profile) throws MalformedURLException {
    return getToken(uri.toString(), profile);
  }

  /**
   * Returns the authorization token for {@code uri}, authenticating with an AWS profile.
   *
   * @param uri     the CodeArtifact repository url
   * @param profile the AWS profile to authenticate with, or {@code null} to let the AWS SDK resolve the credentials
   * @return the authorization token, from the cache when this authentication and url were already resolved
   * @throws MalformedURLException when {@code uri} is not a valid CodeArtifact repository url
   */
  public String getToken(String uri, String profile) throws MalformedURLException {
    CodeArtifactUrl codeArtifactUrl = CodeArtifactUrl.of(uri);

    return tokensCache
      .computeIfAbsent("profile:" + profile + "@" + uri,
        k -> TokenFactory.getAuthorizationToken(codeArtifactUrl, profile).authorizationToken());
  }

  /**
   * Returns the authorization token for {@code uri}, authenticating with the static credentials of a service account.
   *
   * @param uri         the CodeArtifact repository url
   * @param credentials the service account credentials to authenticate with
   * @return the authorization token, from the cache when this authentication and url were already resolved
   * @throws MalformedURLException when {@code uri} is not a valid CodeArtifact repository url
   */
  public String getToken(URI uri, CodeArtifactCredentials credentials) throws MalformedURLException {
    return getToken(uri.toString(), credentials);
  }

  /**
   * Returns the authorization token for {@code uri}, authenticating with the static credentials of a service account.
   *
   * @param uri         the CodeArtifact repository url
   * @param credentials the service account credentials to authenticate with
   * @return the authorization token, from the cache when this authentication and url were already resolved
   * @throws MalformedURLException when {@code uri} is not a valid CodeArtifact repository url
   */
  public String getToken(String uri, CodeArtifactCredentials credentials) throws MalformedURLException {
    CodeArtifactUrl codeArtifactUrl = CodeArtifactUrl.of(uri);

    return tokensCache
      .computeIfAbsent(credentials.cacheKey() + "@" + uri,
        k -> TokenFactory.getAuthorizationToken(codeArtifactUrl, credentials).authorizationToken());
  }

  /**
   * {@inheritDoc}
   *
   * <p>The service takes no parameters.
   */
  @Override
  public None getParameters() {
    return null;
  }
}
