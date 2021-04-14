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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.MalformedURLException;
import org.junit.jupiter.api.Test;

class CodeArtifactUrlTest {

  @Test
  void testOfWithUrl() throws MalformedURLException {
    // when
    CodeArtifactUrl url = CodeArtifactUrl.of("https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/");

    // then
    assertThat(url.getRegion()).isEqualTo("eu-central-1");
    assertThat(url.getArtifactDomain()).isEqualTo("domain");
    assertThat(url.getArtifactOwner()).isEqualTo("id");
    assertThat(url.getPath()).isEqualTo("/maven/repository/");
  }

  @Test
  void testOfWithURLSegments() throws MalformedURLException {
    // Given
    String artifactDomain = "domain";
    String artifactOwner = "owner";
    String region = "eu-west-2";
    String path = "/maven/releases";

    // When
    CodeArtifactUrl url = CodeArtifactUrl.of(artifactDomain, artifactOwner, region, path);

    // Then
    assertThat(url.getUrl()).hasToString("https://domain-owner.d.codeartifact.eu-west-2.amazonaws.com/maven/releases/");
  }
}
