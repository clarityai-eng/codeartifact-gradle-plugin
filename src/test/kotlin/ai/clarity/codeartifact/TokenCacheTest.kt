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

package ai.clarity.codeartifact

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import software.amazon.awssdk.services.codeartifact.model.GetAuthorizationTokenResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class TokenCacheTest {

  @TempDir
  lateinit var directory: Path

  private val now = Instant.parse("2026-01-01T00:00:00Z")
  private val clock = Clock.fixed(now, ZoneOffset.UTC)

  private var fetches = 0

  private fun fetch(token: String, expiresIn: Duration?) = {
    fetches++
    GetAuthorizationTokenResponse.builder()
      .authorizationToken(token)
      .expiration(expiresIn?.let { now.plus(it) })
      .build()
  }

  @Test
  fun `reuses a cached token with enough time left`() {
    val first = TokenCache(directory, clock).get("key", fetch("first", Duration.ofHours(12)))
    val second = TokenCache(directory, clock).get("key", fetch("second", Duration.ofHours(12)))

    assertThat(first).isEqualTo("first")
    assertThat(second).isEqualTo("first")
    assertThat(fetches).isEqualTo(1)
  }

  @Test
  fun `refetches a token close to expiring`() {
    TokenCache(directory, clock).get("key", fetch("first", TokenCache.MINIMUM_REMAINING.minusSeconds(1)))
    val second = TokenCache(directory, clock).get("key", fetch("second", Duration.ofHours(12)))

    assertThat(second).isEqualTo("second")
    assertThat(fetches).isEqualTo(2)
  }

  @Test
  fun `keeps different keys apart`() {
    TokenCache(directory, clock).get("one", fetch("first", Duration.ofHours(12)))
    val other = TokenCache(directory, clock).get("two", fetch("second", Duration.ofHours(12)))

    assertThat(other).isEqualTo("second")
  }

  @Test
  fun `replaces an unreadable entry`() {
    TokenCache(directory, clock).get("key", fetch("first", Duration.ofHours(12)))
    Files.list(directory).use { files -> files.forEach { Files.writeString(it, "expiration=not-a-number") } }

    val second = TokenCache(directory, clock).get("key", fetch("second", Duration.ofHours(12)))

    assertThat(second).isEqualTo("second")
  }

  @Test
  fun `does not cache a token without an expiration`() {
    TokenCache(directory, clock).get("key", fetch("first", null))
    val second = TokenCache(directory, clock).get("key", fetch("second", Duration.ofHours(12)))

    assertThat(second).isEqualTo("second")
  }

  @Test
  fun `does not keep the key in the file name`() {
    TokenCache(directory, clock).get("profile:my-profile@url", fetch("first", Duration.ofHours(12)))

    Files.list(directory).use { files ->
      assertThat(files.map { it.fileName.toString() }.toList()).singleElement().asString()
        .doesNotContain("my-profile")
        .matches("[0-9a-f]{64}\\.properties")
    }
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  fun `writes the token readable by the owner only`() {
    TokenCache(directory, clock).get("key", fetch("first", Duration.ofHours(12)))

    Files.list(directory).use { files ->
      assertThat(Files.getPosixFilePermissions(files.findFirst().orElseThrow()))
        .containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
    }
  }
}
