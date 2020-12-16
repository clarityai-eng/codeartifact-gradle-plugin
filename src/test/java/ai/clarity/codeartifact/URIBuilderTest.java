package ai.clarity.codeartifact;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.Test;

class URIBuilderTest {

  @Test
  void testBasic() throws URISyntaxException {
    URI uri = URI.create("http://www.elpais.es");
    URIBuilder builder = URIBuilder.of(uri);

    assertThat(builder.getScheme()).isEqualTo("http");
    assertThat(builder.getHost()).isEqualTo("www.elpais.es");
    assertThat(builder.toURI()).isEqualTo(uri);
  }

  @Test
  void testGetParam() throws URISyntaxException {
    URI uri = URI.create("http://www.elpais.es/noticias/nacional?pais=francia&foo=bar");
    URIBuilder builder = URIBuilder.of(uri);

    assertThat(builder.getScheme()).isEqualTo("http");
    assertThat(builder.getHost()).isEqualTo("www.elpais.es");
    assertThat(builder.getQueryParamValue("pais")).isEqualTo("francia");
    assertThat(builder.toURI()).isEqualTo(uri);
  }

  @Test
  void testModifyParam() throws URISyntaxException {
    URI uri = URI.create("http://www.elpais.es/noticias/nacional?pais=francia&foo=bar");
    URIBuilder builder = URIBuilder.of(uri);
    builder.setQueryParam("pais", "italia");

    assertThat(builder.getScheme()).isEqualTo("http");
    assertThat(builder.getHost()).isEqualTo("www.elpais.es");
    assertThat(builder.getQueryParamValue("pais")).isEqualTo("italia");
    assertThat(builder.toURI()).isEqualTo(URI.create("http://www.elpais.es/noticias/nacional?pais=italia&foo=bar"));
  }

  @Test
  void testRemoveParam() throws URISyntaxException {
    URI uri = URI.create("http://www.elpais.es/noticias/nacional?pais=francia&foo=bar");
    URIBuilder builder = URIBuilder.of(uri);
    builder.removeQueryParam("pais");

    assertThat(builder.getScheme()).isEqualTo("http");
    assertThat(builder.getHost()).isEqualTo("www.elpais.es");
    assertThat(builder.getQueryParamValue("pais")).isNull();
    assertThat(builder.toURI()).isEqualTo(URI.create("http://www.elpais.es/noticias/nacional?foo=bar"));
  }

  @Test
  void testRemoveAllParams() throws URISyntaxException {
    URI uri = URI.create("http://www.elpais.es/noticias/nacional?pais=francia&foo=bar");
    URIBuilder builder = URIBuilder.of(uri);
    builder.withQuery(null);

    assertThat(builder.getScheme()).isEqualTo("http");
    assertThat(builder.getHost()).isEqualTo("www.elpais.es");
    assertThat(builder.getQueryParamValue("pais")).isNull();
    assertThat(builder.toURI()).isEqualTo(URI.create("http://www.elpais.es/noticias/nacional"));
  }

  @Test
  void testAllFields() throws URISyntaxException {
    URI uri = URI.create("https://testuser:testpass@www.acme.com/test/path?foo=bar#test-fragment");
    URIBuilder builder = URIBuilder.of(uri);

    assertThat(builder.getScheme()).isEqualTo("https");
    assertThat(builder.getUserInfo()).isEqualTo("testuser:testpass");
    assertThat(builder.getHost()).isEqualTo("www.acme.com");
    assertThat(builder.getAuthority()).isEqualTo("testuser:testpass@www.acme.com");
    assertThat(builder.getPath()).isEqualTo("/test/path");
    assertThat(builder.getQuery()).isEqualTo("foo=bar");
    assertThat(builder.getFragment()).isEqualTo("test-fragment");

    assertThat(builder.toURI()).isEqualTo(uri);
  }

  @Test
  void testWithoutPath() throws URISyntaxException {
    URI uri = URI.create("https://www.acme.com?foo=bar");
    URIBuilder builder = URIBuilder.of(uri);

    assertThat(builder.getQueryParamValue("foo")).isEqualTo("bar");
    assertThat(builder.getHost()).isEqualTo("www.acme.com");
    assertThat(builder.toURI()).isEqualTo(uri);
  }

  @Test
  void testParamWithoutValue() throws URISyntaxException {
    URI uri = URI.create("https://testuser:testpass@www.acme.com/test/path?foo");
    URIBuilder builder = URIBuilder.of(uri);

    assertThat(builder.getQueryParamValue("foo")).isNull();
    assertThat(builder.toURI()).isEqualTo(uri);
  }
}