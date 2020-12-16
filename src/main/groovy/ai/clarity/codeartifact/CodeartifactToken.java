package ai.clarity.codeartifact;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceParameters.None;

public class CodeartifactToken implements BuildService<BuildServiceParameters.None> {

  private ConcurrentHashMap<String, String> tokensCache = new ConcurrentHashMap<>();

  public String getToken(URI uri, String profile) throws MalformedURLException {
    return getToken(uri.toString(), profile);
  }

  public String getToken(String uri, String profile) throws MalformedURLException {
    CodeArtifactUrl codeArtifactUrl = CodeArtifactUrl.of(uri);

    return tokensCache
      .computeIfAbsent(profile + "@" + uri, k -> TokenFactory.getAuthorizationToken(codeArtifactUrl, profile).authorizationToken());
  }

  @Override
  public None getParameters() {
    return null;
  }
}
