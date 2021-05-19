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
