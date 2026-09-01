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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
  void testOfWithUrlWithMultiHyphenDomain() throws MalformedURLException {
    // when
    CodeArtifactUrl url = CodeArtifactUrl.of("https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/");

    // then
    assertThat(url.getRegion()).isEqualTo("us-west-2");
    assertThat(url.getArtifactDomain()).isEqualTo("my-domain");
    assertThat(url.getArtifactOwner()).isEqualTo("111122223333");
    assertThat(url.getPath()).isEqualTo("/maven/my-repo/");
  }

  @Test
  void testOfWithInvalidUrl() {
    assertThatThrownBy(() -> CodeArtifactUrl.of("not a valid url"))
      .isInstanceOf(MalformedURLException.class);
  }

  @Test
  void testOfWithDualstackUrl() throws MalformedURLException {
    // given: the shape GetRepositoryEndpoint returns for --endpoint-type dualstack, which unlike the
    // ipv4 one has no ".d." segment
    // when
    CodeArtifactUrl url = CodeArtifactUrl.of("https://my-domain-111122223333.codeartifact.us-west-2.on.aws/maven/my-repo/");

    // then
    assertThat(url.getRegion()).isEqualTo("us-west-2");
    assertThat(url.getArtifactDomain()).isEqualTo("my-domain");
    assertThat(url.getArtifactOwner()).isEqualTo("111122223333");
    assertThat(url.getPath()).isEqualTo("/maven/my-repo/");
  }

  @Test
  void testOfWithDualstackHostCarryingTheIpv4DotDSegment() {
    // given: a host mixing the two shapes, which CodeArtifact never returns
    assertThatThrownBy(
      () -> CodeArtifactUrl.of("https://my-domain-111122223333.d.codeartifact.us-west-2.on.aws/maven/my-repo/"))
      .isInstanceOf(MalformedURLException.class)
      .hasMessageContaining("Not a valid CodeArtifact repository URL");
  }

  @Test
  void testOfWithIpv4HostMissingTheDotDSegment() {
    // given: the mirror image — the amazonaws.com suffix always carries ".d."
    assertThatThrownBy(
      () -> CodeArtifactUrl.of("https://my-domain-111122223333.codeartifact.us-west-2.amazonaws.com/maven/my-repo/"))
      .isInstanceOf(MalformedURLException.class)
      .hasMessageContaining("Not a valid CodeArtifact repository URL");
  }

  @Test
  void testOfWithHostWithoutOwner() {
    assertThatThrownBy(() -> CodeArtifactUrl.of("https://mydomain.d.codeartifact.eu-west-1.amazonaws.com/maven/repository/"))
      .isInstanceOf(MalformedURLException.class)
      .hasMessageContaining("Not a valid CodeArtifact repository URL")
      .hasMessageContaining("https://mydomain.d.codeartifact.eu-west-1.amazonaws.com/maven/repository/")
      .hasMessageContaining("{domain}-{owner}.d.codeartifact.{region}.amazonaws.com");
  }

  @Test
  void testOfWithNonCodeArtifactUrl() {
    assertThatThrownBy(() -> CodeArtifactUrl.of("https://artifacts.mycompany.com/maven/repository/"))
      .isInstanceOf(MalformedURLException.class)
      .hasMessageContaining("Not a valid CodeArtifact repository URL");
  }

  @Test
  void testOfWithVpcEndpointUrl() {
    assertThatThrownBy(() -> CodeArtifactUrl.of(
      "https://vpce-0743fe535b883ffff-76ddffff.d.codeartifact.us-west-2.vpce.amazonaws.com/maven/d/my-domain-111122223333/my-repo/"))
      .isInstanceOf(MalformedURLException.class)
      .hasMessageContaining("Not a valid CodeArtifact repository URL");
  }

  @Test
  void testOfWithUppercaseHost() throws MalformedURLException {
    // when
    CodeArtifactUrl url = CodeArtifactUrl.of("https://my-domain-111122223333.d.CodeArtifact.us-west-2.amazonaws.com/maven/my-repo/");

    // then
    assertThat(url.getRegion()).isEqualTo("us-west-2");
    assertThat(url.getArtifactDomain()).isEqualTo("my-domain");
    assertThat(url.getArtifactOwner()).isEqualTo("111122223333");
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

  @Test
  void testOfWithURLSegmentsNormalizesPathWithoutLeadingSlash() throws MalformedURLException {
    // When
    CodeArtifactUrl url = CodeArtifactUrl.of("domain", "owner", "eu-west-2", "maven/releases");

    // Then
    assertThat(url.getUrl()).hasToString("https://domain-owner.d.codeartifact.eu-west-2.amazonaws.com/maven/releases/");
  }
}
