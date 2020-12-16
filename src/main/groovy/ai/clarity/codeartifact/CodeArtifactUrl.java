package ai.clarity.codeartifact;

import java.net.MalformedURLException;
import java.net.URL;

class CodeArtifactUrl {

  private final URL url;
  private final String artifactDomain;
  private final String artifactOwner;
  private final String region;
  private final String path;

  public CodeArtifactUrl(URL url) {
    this.url = url;
    String[] domainLevels = this.url.getHost().split("\\.");
    path = url.getPath();
    artifactDomain = domainLevels[0].substring(0, domainLevels[0].lastIndexOf("-"));
    artifactOwner = domainLevels[0].substring(domainLevels[0].lastIndexOf("-") + 1);
    region = domainLevels[domainLevels.length - 3];
  }

  public CodeArtifactUrl(String artifactDomain, String artifactOwner, String region, String path) throws MalformedURLException {
    this.artifactDomain = artifactDomain;
    this.artifactOwner = artifactOwner;
    this.region = region;
    this.path = normalizePath(path);
    url = new URL(String.format("https://%s-%s.d.codeartifact.%s.amazonaws.com/%s", artifactDomain, artifactOwner, region, this.path));
  }

  public static CodeArtifactUrl of(String url) throws MalformedURLException {
    return of(new URL(url));
  }

  public static CodeArtifactUrl of(String artifactDomain, String artifactOwner, String region, String path) throws MalformedURLException {
    return new CodeArtifactUrl(artifactDomain, artifactOwner, region, path);
  }

  public static CodeArtifactUrl of(URL url) {
    return new CodeArtifactUrl(url);
  }

  private static String normalizePath(String path) {
    if (path.startsWith("/")) {
      path = path.substring(1);
    }
    if (!path.endsWith("/")) {
      path += "/";
    }

    return path;
  }

  public URL getUrl() {
    return url;
  }

  public String getArtifactDomain() {
    return artifactDomain;
  }

  public String getArtifactOwner() {
    return artifactOwner;
  }

  public String getRegion() {
    return region;
  }

  public String getPath() {
    return path;
  }
}
