package ai.clarity.codeartifact;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class URIBuilder {

  private String scheme;
  private String fragment;
  private String authority;
  private String userInfo;
  private String host;
  private int port = -1;
  private String path;
  private Map<String, String> queryParams = new LinkedHashMap<>();

  private URIBuilder() {

  }

  public static URIBuilder of(URI uri) {
    return new URIBuilder()
      .withScheme(uri.getScheme())
      .withFragment(uri.getFragment())
      .withAuthority(uri.getAuthority())
      .withUserInfo(uri.getUserInfo())
      .withHost(uri.getHost())
      .withPort(uri.getPort())
      .withPath(uri.getPath())
      .withQuery(uri.getQuery());
  }

  public URIBuilder withScheme(String scheme) {
    this.scheme = scheme;
    return this;
  }

  public URIBuilder withFragment(String fragment) {
    this.fragment = fragment;
    return this;
  }

  public URIBuilder withAuthority(String authority) {
    this.authority = authority;
    return this;
  }

  public URIBuilder withUserInfo(String userInfo) {
    this.userInfo = userInfo;
    return this;
  }

  public URIBuilder withHost(String host) {
    this.host = host;
    return this;
  }

  public URIBuilder withPort(int port) {
    this.port = port;
    return this;
  }

  public URIBuilder withPath(String path) {
    this.path = path;
    return this;
  }

  public URIBuilder withQuery(String query) {
    queryParams = parseQueryParams(query);
    return this;
  }

  public String getQueryParamValue(String param) {
    return queryParams.get(param);
  }

  public URIBuilder removeQueryParam(String param) {
    queryParams.remove(param);
    return this;
  }

  public URIBuilder setQueryParam(String param, String value) {
    queryParams.put(param, value);
    return this;
  }

  public URI toURI() throws URISyntaxException {
    String query = generateQueryByParams();
    return new URI(scheme, userInfo, host, port, path, query, fragment);
  }

  private String generateQueryByParams() {
    String query = queryParams.entrySet().stream()
      .map(e -> {
        String txt = e.getKey();
        if (e.getValue() != null) {
          txt += "=" + e.getValue();
        }

        return txt;
      })
      .collect(Collectors.joining("&"));

    if (query.isEmpty()) {
      query = null;
    }

    return query;
  }

  private Map<String, String> parseQueryParams(String query) {
    Map<String, String> params = new LinkedHashMap<>();
    if (query != null) {
      if (query.startsWith("?")) {
        query = query.substring(1);
      }
      String[] split = query.split("&");
      for (String nameValue : split) {
        String name;
        String value;
        int equalsIndex = nameValue.indexOf('=');
        if (equalsIndex > 0) {
          name = nameValue.substring(0, equalsIndex);
          value = nameValue.substring(equalsIndex + 1);
        } else {
          name = nameValue;
          value = null;
        }

        params.put(name, value);
      }
    }

    return params;
  }

  public String getScheme() {
    return scheme;
  }

  public String getFragment() {
    return fragment;
  }

  public String getAuthority() {
    return authority;
  }

  public String getUserInfo() {
    return userInfo;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public String getPath() {
    return path;
  }

  public String getQuery() {
    return generateQueryByParams();
  }
}
