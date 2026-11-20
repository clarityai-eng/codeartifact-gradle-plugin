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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Properties;
import java.util.function.Supplier;
import software.amazon.awssdk.services.codeartifact.model.GetAuthorizationTokenResponse;

/**
 * Keeps CodeArtifact authorization tokens on disk between builds, one owner-only file per cache key.
 *
 * <p>A token is reused while it has at least {@link #MINIMUM_REMAINING} left, so that it does not expire part way
 * through a build. The key is stored only as a digest, so the file name reveals neither profile nor credentials.
 */
final class TokenCache {

  static final Duration MINIMUM_REMAINING = Duration.ofMinutes(15);

  private static final String TOKEN = "token";
  private static final String EXPIRATION = "expiration";

  private final Path directory;
  private final Clock clock;

  TokenCache(Path directory, Clock clock) {
    this.directory = directory;
    this.clock = clock;
  }

  String get(String key, Supplier<GetAuthorizationTokenResponse> fetch) {
    Path file = directory.resolve(sha256(key) + ".properties");
    String cached = read(file);
    if (cached != null) {
      return cached;
    }
    GetAuthorizationTokenResponse response = fetch.get();
    write(file, response);
    return response.authorizationToken();
  }

  private String read(Path file) {
    if (!Files.isRegularFile(file)) {
      return null;
    }
    Properties properties = new Properties();
    try (InputStream in = Files.newInputStream(file)) {
      properties.load(in);
      String token = properties.getProperty(TOKEN);
      Instant expiration = Instant.ofEpochSecond(Long.parseLong(properties.getProperty(EXPIRATION)));
      if (token == null || expiration.isBefore(clock.instant().plus(MINIMUM_REMAINING))) {
        return null;
      }
      return token;
    } catch (IOException | RuntimeException e) {
      // An unreadable or truncated entry is replaced by the next fetch
      return null;
    }
  }

  private void write(Path file, GetAuthorizationTokenResponse response) {
    if (response.expiration() == null) {
      return;
    }
    Properties properties = new Properties();
    properties.setProperty(TOKEN, response.authorizationToken());
    properties.setProperty(EXPIRATION, Long.toString(response.expiration().getEpochSecond()));
    try {
      Files.createDirectories(directory);
      // createTempFile is owner-only on POSIX file systems, and the move keeps those permissions
      Path temp = Files.createTempFile(directory, "token", ".tmp");
      try {
        try (OutputStream out = Files.newOutputStream(temp)) {
          properties.store(out, null);
        }
        move(temp, file);
      } finally {
        Files.deleteIfExists(temp);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Could not cache the CodeArtifact token in " + directory, e);
    }
  }

  private static void move(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
