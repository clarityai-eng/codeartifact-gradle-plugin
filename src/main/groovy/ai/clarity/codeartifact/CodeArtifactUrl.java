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
