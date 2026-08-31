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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;

/**
 * Static AWS credentials of a service account, used to request the CodeArtifact authorization token without relying on
 * a local AWS profile.
 *
 * <p>A session token can be supplied for temporary credentials, and must be omitted for the long lived access keys of
 * an IAM user.
 */
public final class CodeArtifactCredentials {

  private static final String ACCESS_KEY_ID = "accessKeyId";
  private static final String SECRET_ACCESS_KEY = "secretAccessKey";
  private static final String SESSION_TOKEN = "sessionToken";

  private static final Set<String> SUPPORTED_KEYS = Set.of(ACCESS_KEY_ID, SECRET_ACCESS_KEY, SESSION_TOKEN);

  private final String accessKeyId;
  private final String secretAccessKey;
  private final String sessionToken;

  private CodeArtifactCredentials(String accessKeyId, String secretAccessKey, String sessionToken) {
    this.accessKeyId = requireText(accessKeyId, ACCESS_KEY_ID);
    this.secretAccessKey = requireText(secretAccessKey, SECRET_ACCESS_KEY);
    this.sessionToken = emptyToNull(sessionToken);
  }

  public static CodeArtifactCredentials of(String accessKeyId, String secretAccessKey) {
    return new CodeArtifactCredentials(accessKeyId, secretAccessKey, null);
  }

  public static CodeArtifactCredentials of(String accessKeyId, String secretAccessKey, String sessionToken) {
    return new CodeArtifactCredentials(accessKeyId, secretAccessKey, sessionToken);
  }

  /**
   * Builds the credentials from a map, which is the shape the Groovy DSL passes them in, as in
   * {@code codeartifact(url, [accessKeyId: '...', secretAccessKey: '...'])}.
   */
  public static CodeArtifactCredentials of(Map<?, ?> values) {
    Set<String> unsupported = new LinkedHashSet<>();
    for (Object key : values.keySet()) {
      String name = String.valueOf(key);
      if (!SUPPORTED_KEYS.contains(name)) {
        unsupported.add(name);
      }
    }
    if (!unsupported.isEmpty()) {
      throw new IllegalArgumentException(
        "Unsupported CodeArtifact credentials " + unsupported + ", expected any of " + SUPPORTED_KEYS);
    }

    return new CodeArtifactCredentials(
      asString(values.get(ACCESS_KEY_ID), ACCESS_KEY_ID),
      asString(values.get(SECRET_ACCESS_KEY), SECRET_ACCESS_KEY),
      asString(values.get(SESSION_TOKEN), SESSION_TOKEN));
  }

  public String getAccessKeyId() {
    return accessKeyId;
  }

  /**
   * The access key id with its middle section hidden, safe to write to the build log.
   */
  public String getMaskedAccessKeyId() {
    if (accessKeyId.length() <= 8) {
      return "*".repeat(accessKeyId.length());
    }

    return accessKeyId.substring(0, 4) + "*".repeat(accessKeyId.length() - 8) + accessKeyId.substring(accessKeyId.length() - 4);
  }

  AwsCredentials toAwsCredentials() {
    if (sessionToken == null) {
      return AwsBasicCredentials.create(accessKeyId, secretAccessKey);
    }

    return AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken);
  }

  /**
   * Identifies these credentials in the token cache. It is a digest so that no secret is kept in clear as a cache key.
   */
  String cacheKey() {
    return "credentials:" + digest(accessKeyId + '\0' + secretAccessKey + '\0' + sessionToken);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CodeArtifactCredentials other)) {
      return false;
    }

    return accessKeyId.equals(other.accessKeyId)
      && secretAccessKey.equals(other.secretAccessKey)
      && Objects.equals(sessionToken, other.sessionToken);
  }

  @Override
  public int hashCode() {
    return Objects.hash(accessKeyId, secretAccessKey, sessionToken);
  }

  /**
   * Redacted on purpose: these credentials end up in build logs and in Gradle error messages.
   */
  @Override
  public String toString() {
    return "CodeArtifactCredentials{accessKeyId=" + getMaskedAccessKeyId()
      + ", sessionToken=" + (sessionToken == null ? "absent" : "present") + "}";
  }

  private static String digest(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available in this JVM", e);
    }
  }

  private static String asString(Object value, String name) {
    if (value == null || value instanceof CharSequence) {
      return value == null ? null : value.toString();
    }
    // The value itself is never part of the message, it may be the secret access key
    throw new IllegalArgumentException("The CodeArtifact credentials " + name + " must be a String but is a "
      + value.getClass().getName() + ", call get() on it if it is a Gradle provider");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("The CodeArtifact credentials " + name + " is required and cannot be blank");
    }

    return value;
  }

  private static String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
