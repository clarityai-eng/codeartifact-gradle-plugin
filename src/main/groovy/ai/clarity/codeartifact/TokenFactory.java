package ai.clarity.codeartifact;

import java.net.MalformedURLException;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.CodeartifactClientBuilder;
import software.amazon.awssdk.services.codeartifact.model.GetAuthorizationTokenResponse;

public class TokenFactory {

  public static GetAuthorizationTokenResponse getAuthorizationToken(String codeArtifactUrl, String profileName)
    throws MalformedURLException {
    return getAuthorizationToken(CodeArtifactUrl.of(codeArtifactUrl), profileName);
  }

  public static GetAuthorizationTokenResponse getAuthorizationToken(CodeArtifactUrl codeArtifactUrl, String profileName) {
    CodeartifactClientBuilder builder = CodeartifactClient.builder()
      .region(Region.of(codeArtifactUrl.getRegion()));

    if (profileName != null) {
      builder = builder.credentialsProvider(ProfileCredentialsProvider.create(profileName));
    }
    CodeartifactClient client = builder.build();

    return client
      .getAuthorizationToken(req -> req.domain(codeArtifactUrl.getArtifactDomain()).domainOwner(codeArtifactUrl.getArtifactOwner()));
  }
}
