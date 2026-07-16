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
import java.net.URISyntaxException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class CodeArtifactUrl {

  // Accepts the public CodeArtifact repository hosts, both the standard endpoint
  // ({domain}-{owner}.d.codeartifact.{region}.amazonaws.com) and the dualstack one
  // ({domain}-{owner}.d.codeartifact.{region}.on.aws). VPC endpoints
  // (vpce-*.d.codeartifact.{region}.vpce.amazonaws.com) carry the domain and owner in
  // the path instead of the host and are not supported.
  private static final Pattern HOST_PATTERN = Pattern.compile(
    "(?i)^([^.]+)-([^-.]+)\\.d\\.codeartifact\\.([^.]+)\\.(?:amazonaws\\..+|on\\.aws)$");

  private static final String EXPECTED_FORMAT =
    "https://{domain}-{owner}.d.codeartifact.{region}.amazonaws.com/{format}/{repository}/";

  private final URL url;
  private final String artifactDomain;
  private final String artifactOwner;
  private final String region;
  private final String path;

  public CodeArtifactUrl(URL url) throws MalformedURLException {
    Matcher host = HOST_PATTERN.matcher(url.getHost());
    if (!host.matches()) {
      throw new MalformedURLException(
        "Not a valid CodeArtifact repository URL: " + url + " (expected format: " + EXPECTED_FORMAT + ")");
    }
    this.url = url;
    path = url.getPath();
    artifactDomain = host.group(1);
    artifactOwner = host.group(2);
    region = host.group(3);
  }

  public CodeArtifactUrl(String artifactDomain, String artifactOwner, String region, String path) throws MalformedURLException {
    this.artifactDomain = artifactDomain;
    this.artifactOwner = artifactOwner;
    this.region = region;
    this.path = normalizePath(path);
    try {
      url = new URI(
        String.format("https://%s-%s.d.codeartifact.%s.amazonaws.com/%s", artifactDomain, artifactOwner, region, this.path)).toURL();
    } catch (URISyntaxException e) {
      throw new MalformedURLException(e.getMessage());
    }
  }

  public static CodeArtifactUrl of(String url) throws MalformedURLException {
    try {
      return of(new URI(url).toURL());
    } catch (URISyntaxException e) {
      throw new MalformedURLException(e.getMessage());
    }
  }

  public static CodeArtifactUrl of(String artifactDomain, String artifactOwner, String region, String path) throws MalformedURLException {
    return new CodeArtifactUrl(artifactDomain, artifactOwner, region, path);
  }

  public static CodeArtifactUrl of(URL url) throws MalformedURLException {
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
