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

public class TokenFactory {

  public static GetAuthorizationTokenResponse getAuthorizationToken(String codeArtifactUrl, String profileName)
    throws MalformedURLException {
    return getAuthorizationToken(CodeArtifactUrl.of(codeArtifactUrl), profileName);
  }

  /**
   * Requests the token with the given AWS profile, or with the default credentials provider chain when
   * {@code profileName} is {@code null}.
   */
  public static GetAuthorizationTokenResponse getAuthorizationToken(CodeArtifactUrl codeArtifactUrl, String profileName) {
    return requestToken(codeArtifactUrl, profileName == null ? null : ProfileCredentialsProvider.create(profileName));
  }

  /**
   * Requests the token with the static credentials of a service account, bypassing the local AWS profiles.
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
