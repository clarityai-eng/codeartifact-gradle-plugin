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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

  /**
   * Mutable builder for editing the parts of a {@link URI}, used here to read and strip the {@code ?profile=} query
   * parameter of a repository url without rebuilding the string by hand.
   *
   * <p>Query parameters keep their insertion order, and a parameter present with no {@code =} is held with a
   * {@code null} value so that it survives a round trip.
   */
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

  /**
   * Creates a builder holding every component of {@code uri}.
   *
   * @param uri the uri to read
   * @return a builder initialised from {@code uri}
   */
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

  /**
   * Sets the scheme component.
   *
   * @param scheme the scheme, such as {@code https}
   * @return this builder
   */
  public URIBuilder withScheme(String scheme) {
    this.scheme = scheme;
    return this;
  }

  /**
   * Sets the fragment component.
   *
   * @param fragment the fragment, without the leading {@code #}
   * @return this builder
   */
  public URIBuilder withFragment(String fragment) {
    this.fragment = fragment;
    return this;
  }

  /**
   * Sets the authority component.
   *
   * @param authority the authority
   *
   * <p>Note {@link #toURI()} rebuilds the authority from the user info, host and port instead of using this
   * value, so setting it alone does not change the uri produced.
   * @return this builder
   */
  public URIBuilder withAuthority(String authority) {
    this.authority = authority;
    return this;
  }

  /**
   * Sets the userInfo component.
   *
   * @param userInfo the user info, without the trailing {@code @}
   * @return this builder
   */
  public URIBuilder withUserInfo(String userInfo) {
    this.userInfo = userInfo;
    return this;
  }

  /**
   * Sets the host component.
   *
   * @param host the host
   * @return this builder
   */
  public URIBuilder withHost(String host) {
    this.host = host;
    return this;
  }

  /**
   * Sets the port component.
   *
   * @param port the port, or {@code -1} for none
   * @return this builder
   */
  public URIBuilder withPort(int port) {
    this.port = port;
    return this;
  }

  /**
   * Sets the path component.
   *
   * @param path the path, with its leading {@code /}
   * @return this builder
   */
  public URIBuilder withPath(String path) {
    this.path = path;
    return this;
  }

  /**
   * Replaces every query parameter with the ones parsed from {@code query}.
   *
   * @param query the raw query string, without the leading {@code ?}; {@code null} clears the parameters
   * @return this builder
   */
  public URIBuilder withQuery(String query) {
    queryParams = parseQueryParams(query);
    return this;
  }

  /**
   * Returns the value of one query parameter.
   *
   * @param param the parameter name
   * @return its value, {@code null} when the parameter is absent <em>or</em> present without a value
   */
  public String getQueryParamValue(String param) {
    return queryParams.get(param);
  }

  /**
   * Removes one query parameter, doing nothing when it is absent.
   *
   * @param param the parameter name
   * @return this builder
   */
  public URIBuilder removeQueryParam(String param) {
    queryParams.remove(param);
    return this;
  }

  /**
   * Sets one query parameter, replacing any previous value and keeping the original position when it was already
   * present.
   *
   * @param param the parameter name
   * @param value its value, or {@code null} to render the parameter with no {@code =}
   * @return this builder
   */
  public URIBuilder setQueryParam(String param, String value) {
    queryParams.put(param, value);
    return this;
  }

  /**
   * Builds the uri from the components held by this builder.
   *
   * @return the resulting uri, with the query rendered from the current parameters
   * @throws URISyntaxException when the components do not form a valid uri
   */
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

  /**
   * Returns the scheme component.
   *
   * @return the scheme
   */
  public String getScheme() {
    return scheme;
  }

  /**
   * Returns the fragment component.
   *
   * @return the fragment
   */
  public String getFragment() {
    return fragment;
  }

  /**
   * Returns the authority component.
   *
   * @return the authority as read from the original uri; see {@link #withAuthority(String)} for why
   *         {@link #toURI()} ignores it
   */
  public String getAuthority() {
    return authority;
  }

  /**
   * Returns the user info component.
   *
   * @return the user info
   */
  public String getUserInfo() {
    return userInfo;
  }

  /**
   * Returns the host component.
   *
   * @return the host
   */
  public String getHost() {
    return host;
  }

  /**
   * Returns the port component.
   *
   * @return the port, or {@code -1} when there is none
   */
  public int getPort() {
    return port;
  }

  /**
   * Returns the path component.
   *
   * @return the path
   */
  public String getPath() {
    return path;
  }

  /**
   * Renders the current query parameters back into a query string.
   *
   * @return the query string, without the leading {@code ?}, or {@code null} when there are no parameters
   */
  public String getQuery() {
    return generateQueryByParams();
  }
}
