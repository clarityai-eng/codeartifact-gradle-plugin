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
