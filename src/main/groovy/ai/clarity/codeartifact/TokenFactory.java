package ai.clarity.codeartifact;

import java.net.MalformedURLException;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.GetAuthorizationTokenResponse;

public class TokenFactory {

  public static GetAuthorizationTokenResponse getAuthorizationToken(String codeArtifactUrl, String profileName)
    throws MalformedURLException {
    return getAuthorizationToken(CodeArtifactUrl.of(codeArtifactUrl), profileName);
  }

  public static GetAuthorizationTokenResponse getAuthorizationToken(CodeArtifactUrl codeArtifactUrl, String profileName) {
    CodeartifactClient client = CodeartifactClient.builder()
      .credentialsProvider(ProfileCredentialsProvider.create(profileName))
      .region(Region.of(codeArtifactUrl.getRegion()))
      .build();

    return client
      .getAuthorizationToken(req -> req.domain(codeArtifactUrl.getArtifactDomain()).domainOwner(codeArtifactUrl.getArtifactOwner()));
  }
}
