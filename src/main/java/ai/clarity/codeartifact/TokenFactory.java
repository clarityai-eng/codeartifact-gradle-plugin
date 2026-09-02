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
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.CodeartifactClientBuilder;
import software.amazon.awssdk.services.codeartifact.model.GetAuthorizationTokenResponse;

/**
 * Calls the CodeArtifact {@code GetAuthorizationToken} API.
 *
 * <p>Every overload issues a request; the caching lives in {@link CodeArtifactToken}.
 */
public class TokenFactory {

  private TokenFactory() {
    // Static-only utility
  }

  /**
   * Requests the token for a repository url with the given AWS profile.
   *
   * @param codeArtifactUrl the CodeArtifact repository url
   * @param profileName     the AWS profile to authenticate with, or {@code null} to use the default credentials
   *                        provider chain
   * @return the API response carrying the authorization token and its expiration
   * @throws MalformedURLException when {@code codeArtifactUrl} is not a valid CodeArtifact repository url
   */
  public static GetAuthorizationTokenResponse getAuthorizationToken(String codeArtifactUrl, String profileName)
    throws MalformedURLException {
    return getAuthorizationToken(CodeArtifactUrl.of(codeArtifactUrl), profileName);
  }

  /**
   * Requests the token with the given AWS profile, or with the default credentials provider chain when
   * {@code profileName} is {@code null}.
   *
   * @param codeArtifactUrl the parsed CodeArtifact repository url, which carries the region the client targets
   * @param profileName     the AWS profile to authenticate with, or {@code null} to use the default credentials
   *                        provider chain
   * @return the API response carrying the authorization token and its expiration
   */
  public static GetAuthorizationTokenResponse getAuthorizationToken(CodeArtifactUrl codeArtifactUrl, String profileName) {
    return requestToken(codeArtifactUrl, profileName == null ? null : ProfileCredentialsProvider.create(profileName));
  }

  /**
   * Requests the token with the static credentials of a service account, bypassing the local AWS profiles.
   *
   * @param codeArtifactUrl the parsed CodeArtifact repository url, which carries the region the client targets
   * @param credentials     the service account credentials to authenticate with
   * @return the API response carrying the authorization token and its expiration
   */
  public static GetAuthorizationTokenResponse getAuthorizationToken(CodeArtifactUrl codeArtifactUrl,
                                                                    CodeArtifactCredentials credentials) {
    return requestToken(codeArtifactUrl, StaticCredentialsProvider.create(credentials.toAwsCredentials()));
  }

  private static GetAuthorizationTokenResponse requestToken(CodeArtifactUrl codeArtifactUrl,
                                                            AwsCredentialsProvider credentialsProvider) {
    CodeartifactClientBuilder builder = CodeartifactClient.builder()
      .region(Region.of(codeArtifactUrl.getRegion()));

    if (credentialsProvider != null) {
      builder = builder.credentialsProvider(credentialsProvider);
    }
    CodeartifactClient client = builder.build();

    return client
      .getAuthorizationToken(req -> req.domain(codeArtifactUrl.getArtifactDomain()).domainOwner(codeArtifactUrl.getArtifactOwner()));
  }
}
