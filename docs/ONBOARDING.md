# Onboarding

Everything here was executed against this checkout (`main`, 2026-09-01) — no claim is copied
from `README.md` without verification. The last section lists what the README gets wrong.

## 1. What the plugin does

Consumers declare an ordinary Maven repository pointing at an AWS CodeArtifact endpoint. At
Gradle **configuration time** the plugin:

1. spots repositories whose URL matches `.+\.codeartifact\..+\.amazonaws\..+` (case-insensitive);
2. resolves how to authenticate — the static keys of a service account, or an AWS profile;
3. calls `codeartifact:GetAuthorizationToken` through the AWS SDK v2;
4. sets `username = "aws"`, `password = <token>` on the repository;
5. removes the `?profile=` query parameter from the URL so AWS never sees it.

There is no task, no extension block, and nothing for the consumer to invoke.

## 2. Requirements

| | |
|---|---|
| JDK to build | 21 (Gradle toolchain; the launcher JVM may be newer — verified on JDK 25) |
| Gradle to build | wrapper, 9.7.1 |
| Gradle to consume | 8.4+ documented and tested (8.3 also works in practice, see §7) |
| AWS | a resolvable credential chain — service-account keys (§5), profile, SSO, env vars, instance role… |

Building this repo itself needs **no AWS access**: it resolves only from Maven Central.

```bash
git clone git@github.com:clarityai-eng/codeartifact-gradle-plugin.git
cd codeartifact-gradle-plugin
./gradlew build
```

Gradle cannot run inside the Claude Code sandbox — disable it for every `./gradlew` call.

## 3. Architecture

```
        consumer build                 this plugin
  ┌──────────────────────┐   apply   ┌────────────────────────────────────┐
  │ build.gradle(.kts)   │──────────▶│ ClarityCodeArtifactGradlePlugin    │  Plugin<Any>
  │ settings.gradle(.kts)│           │   target is Project  ─▶ CodeArtifactProjectPlugin
  └──────────────────────┘           │   target is Settings ─▶ CodeArtifactSettingsPlugin
                                     │   anything else      ─▶ IllegalArgumentException
                                     └───────────────┬────────────────────┘
                                                     │ both delegate to
                                     ┌───────────────▼────────────────────┐
                                     │ CodeartifactRepositoryConfigurer   │
                                     │  · registers the codeartifact()    │
                                     │    Groovy closure + stashes the    │
                                     │    BuildService in extraProperties │
                                     │  · withType(Maven…).configureEach: │
                                     │    detect ▸ authenticate ▸ creds   │
                                     └───────────────┬────────────────────┘
                                                     │ delegates the choice to
                                     ┌───────────────▼────────────────────┐
                                     │ CodeArtifactAuthenticator          │
                                     │   repo credentials ▸ repo profile  │
                                     │   ▸ CodeArtifactCredentialsResolver│
                                     │     (build-wide static keys)       │
                                     │   ▸ fallback profile               │
                                     └───────────────┬────────────────────┘
                                                     │
                       ┌─────────────────────────────▼──────────────────┐
                       │ CodeArtifactToken  (Gradle BuildService)        │
                       │   ConcurrentHashMap<auth+url, token>            │
                       └───────────────┬────────────────────────────────┘
                                       │
                    CodeArtifactUrl ───┴──▶ TokenFactory ──▶ AWS CodeArtifact
                    (host ▸ domain,            (SDK v2,        GetAuthorizationToken
                     owner, region)             optional
                                                ProfileCredentialsProvider)
```

### Entry points

| File | Role |
|---|---|
| `ClarityCodeArtifactGradlePlugin.kt` | `implementationClass` in `build.gradle.kts`; a `Plugin<Any>` that dispatches on target type. Also declares the top-level Kotlin extension `RepositoryHandler.codeartifact(url, profile, action)`. |
| `CodeArtifactProjectPlugin.kt` | Configures `project.repositories` and, when `maven-publish` is present, `publishing.repositories`. |
| `CodeArtifactSettingsPlugin.kt` | Configures `settings.pluginManagement.repositories` and `settings.dependencyResolutionManagement.repositories`. |
| `CodeartifactRepositoryConfigurer.kt` | The only place detection and credential injection live. |
| `CodeArtifactAuthenticator.kt` | The only place the precedence between repository credentials, repository profile, build-wide service credentials and fallback profile is decided. |
| `CodeArtifactCredentialsResolver.kt` | Reads the build-wide service-account keys from system properties, then environment variables. Takes the lookups as parameters so tests need not mutate the JVM. |

### Support classes (Java)

- **`CodeArtifactToken`** — `BuildService<None>`, registered as `"codeartifact-token"`, caching by
  `"profile:<name>@<url>"` or `"credentials:<sha256>@<url>"`. One token per authentication/URL
  pair per build, shared across projects; the two key shapes cannot collide.
- **`CodeArtifactCredentials`** — the static service-account keys. Rejects blank values and
  non-`CharSequence` map entries (naming the key and its type, never its value), exposes only a
  masked access key id, redacts `toString()`, and derives the cache key from a SHA-256 digest.
- **`TokenFactory`** — builds a `CodeartifactClient` for the URL's region. With credentials it
  installs a `StaticCredentialsProvider`; with a non-null profile name a
  `ProfileCredentialsProvider`; with neither it leaves the AWS default credential chain in place
  (which is how `AWS_PROFILE`, SSO and instance roles work).
- **`CodeArtifactUrl`** — parses the host with
  `^([^.]+)-([^-.]+)\.d\.codeartifact\.([^.]+)\.(?:amazonaws\..+|on\.aws)$`, yielding
  domain / owner / region. Rejects anything else with a `MalformedURLException` naming the
  expected format. VPC endpoints are deliberately unsupported (they carry domain and owner in
  the path).
- **`URIBuilder`** — ordered query-param map over `java.net.URI`, used to read and strip
  `?profile=`.

## 4. Consumer usage (all verified against a stubbed CodeArtifact endpoint)

### Automatic detection — the main path

```kotlin
// build.gradle.kts
plugins { id("ai.clarity.codeartifact") version "0.2.0" }
repositories {
  maven { url = uri("https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/") }
}
```

Works identically in Groovy, in `publishing { repositories { … } }` (needs `maven-publish`),
and in `settings.gradle(.kts)` under `pluginManagement { }` / `dependencyResolutionManagement { }`.

### Explicit `codeartifact()` helper

```kotlin
import ai.clarity.codeartifact.codeartifact   // REQUIRED in the Kotlin DSL

repositories {
  codeartifact("https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/", "prod") {
    name = "myCodeArtifactRepo"
  }
}
```

```groovy
// Groovy needs no import — the method is added dynamically to RepositoryHandler
repositories {
  codeartifact('https://…/maven/my-repo/', 'prod') { name = 'myCodeArtifactRepo' }
}
```

The helper also takes the static credentials of a service account instead of a profile:

```kotlin
import ai.clarity.codeartifact.CodeArtifactCredentials
import ai.clarity.codeartifact.codeartifact

repositories {
  codeartifact(
    "https://my-domain-111122223333.d.codeartifact.us-west-2.amazonaws.com/maven/my-repo/",
    CodeArtifactCredentials.of(accessKeyId, secretAccessKey)   // third arg: sessionToken
  )
}
```

```groovy
// Groovy takes a map, and rejects an unknown key by name
repositories {
  codeartifact('https://…/maven/my-repo/', [accessKeyId: id, secretAccessKey: secret])
}
```

Two traps:

- **Without the Kotlin imports you get `Unresolved reference 'codeartifact'`** (a fully-qualified
  `ai.clarity.codeartifact.codeartifact(...)` call does not work either — it is an extension
  function). `CodeArtifactCredentials` needs its own import.
- **With no profile and no service credentials the helper falls back to the literal `"default"`
  profile**, not to the rest of the chain below. With `CODEARTIFACT_PROFILE=dev` set,
  `codeartifact(url)` still logs `in profile default`. Pass the profile explicitly if you need
  another one. Build-wide service credentials *are* honoured on this path.

Both work in `dependencyResolutionManagement` inside `settings.gradle(.kts)` too, but **not**
in `pluginManagement`: that block is evaluated before `plugins { }` applies the plugin, so the
method does not exist yet (`Could not find method codeartifact()`).

## 5. Credentials resolution

Verified order, highest precedence first. Steps 1 and 2 are per repository, the rest are
build-wide:

| # | Source | Verified behaviour |
|---|---|---|
| 1 | `CodeArtifactCredentials` passed to `codeartifact()` (Kotlin) or a credentials map (Groovy) | wins over everything |
| 2 | `?profile=<name>` in the URL, or a profile passed to `codeartifact()` | beats every build-wide setting; the query param is stripped from the final URL |
| 3 | `codeartifact.accessKeyId` + `codeartifact.secretAccessKey` | static service-account keys; optional `codeartifact.sessionToken` for temporary ones |
| 4 | `codeartifact.profile` | the build-wide default profile |
| 5 | nothing → profile is `null` | the AWS SDK default chain runs, so `AWS_PROFILE`, `AWS_ACCESS_KEY_ID`, SSO caches and instance roles apply |

Each `codeartifact.*` setting in steps 3 and 4 is resolved by `SettingLookup` from three sources,
highest precedence first:

| # | Source | Written as |
|---|---|---|
| 1 | Gradle property | a plain `codeartifact.profile=<name>` in `gradle.properties`, or `-Pcodeartifact.profile=<name>` |
| 2 | Java system property | `systemProp.codeartifact.profile=<name>` in `gradle.properties`, or `-Dcodeartifact.profile=<name>` |
| 3 | environment variable | `CODEARTIFACT_PROFILE`, `CODEARTIFACT_ACCESS_KEY_ID`, `CODEARTIFACT_SECRET_ACCESS_KEY`, `CODEARTIFACT_SESSION_TOKEN` |

The first source that *holds* the setting wins, even when the value is blank; a blank value then
resolves to "not configured" rather than falling through. Verified on Gradle 8.4 through 9.7.1,
in `Settings` scripts as well as project ones.

Two asymmetries worth knowing, both measured:

- **Step 4 applies to automatic detection only.** A repository declared with
  `codeartifact()` and no explicit profile falls back to the `default` profile instead, so
  `codeartifact.profile` has no effect on it. Step 3 *does* reach it.
- **Setting exactly one of the two required step-3 values fails the build** with
  `Incomplete CodeArtifact service credentials: …`, naming the missing half — but only when
  steps 1 and 2 did not already settle the authentication. With a `?profile=` in the URL the
  half-configured pair is ignored instead of reported.

**Match the command-line override to the form you committed.** A Gradle property is overridden
with `-P`, a `systemProp.` one with `-D`. Because the Gradle property outranks the system
property, a `-Dcodeartifact.profile=ci` against a committed plain `codeartifact.profile=dev` does
*not* override it. That combination logs a warning naming the setting rather than silently
authenticating with the wrong profile — the values are deliberately left out of the message so a
shadowed `codeartifact.secretAccessKey` cannot leak.

Recommended pattern for teams: commit `codeartifact.profile=dev` and let CI override with
`-Pcodeartifact.profile=ci`. Do not mix it with `?profile=`, which would defeat the override.
CI that has no `~/.aws/credentials` at all should export the `CODEARTIFACT_*` keys instead.

### Secret handling

Never put the keys in the project `gradle.properties` — use `~/.gradle/gradle.properties` or the
CI secret store. There is deliberately no `?accessKeyId=` query param, because the repository URL
reaches build scans, caches and logs. What the plugin does guarantee, all verified:

- only a masked access key id (`AKIA************MPLE`) is logged, never the secret;
- `CodeArtifactCredentials.toString()` is redacted, so a Gradle error message cannot leak it;
- the token cache key hashes the credentials (SHA-256) instead of holding them in clear, and
  profile entries never collide with service-credential entries;
- a failing token request at `--debug --stacktrace` produced 6249 log lines with **0**
  occurrences of the secret and 0 of the unmasked access key id.

## 6. Testing

```bash
./gradlew test --rerun-tasks            # 98 tests, all green
./gradlew functionalTest --rerun-tasks  # 101 tests, all green
./gradlew build                         # both + validatePlugins; ~4m from clean
```

- **Unit tests** use `ProjectBuilder` / `ProjectBuilder`-backed settings and
  `mockkStatic(TokenFactory::class)` so no AWS call happens. `@AfterEach` calls `unmockkAll()`
  and clears `codeartifact.profile`.
- **Functional tests** (`src/functionalTest`) run real builds via `GradleRunner` across
  `8.4, 8.7, 8.14, 9.1.0, 9.4.0`. Most assert on `buildAndFail()` plus the `--info` line
  `Getting token for <url> in profile <p>` — the failure *is* the assertion that AWS was reached.
- One functional test (`TokenEndpointFunctionalTest`) starts a `com.sun.net.httpserver.HttpServer`
  returning `{"authorizationToken":"stub-token"}` and points the SDK at it with
  `AWS_ENDPOINT_URL_CODEARTIFACT`, so it can assert the injected credentials for real. Reuse
  that trick for any manual end-to-end check.

Beware: `test`/`functionalTest` cache aggressively. A green `build` that reports `UP-TO-DATE`
proves nothing about the current sources — pass `--rerun-tasks`.

### Manual end-to-end check without AWS

```bash
./gradlew publishToMavenLocal            # publishes the current -SNAPSHOT
# stub server returning {"authorizationToken":"stub-token"} on any request, e.g. on :18099
# then, in a scratch project:
#   settings.gradle.kts -> pluginManagement { repositories { mavenLocal(); gradlePluginPortal() } }
#   build.gradle.kts    -> plugins { id("ai.clarity.codeartifact") version "<that snapshot>" }
AWS_ENDPOINT_URL_CODEARTIFACT=http://127.0.0.1:18099 \
AWS_SHARED_CREDENTIALS_FILE=/tmp/creds AWS_REGION=us-west-2 \
  ./gradlew help --info
```

Set `AWS_SHARED_CREDENTIALS_FILE` to a throwaway file with fake keys when you exercise a named
profile, since `ProfileCredentialsProvider` bypasses `AWS_ACCESS_KEY_ID`.

## 7. Compatibility

- Badge and functional matrix floor: **Gradle 8.4**.
- Verified locally: **8.3 also works** (`BUILD SUCCESSFUL`, token requested).
- Below that, testing is blocked by a Gradle jar-cache bug
  ([gradle/gradle#34505](https://github.com/gradle/gradle/issues/34505)):
  8.0.2 fails with `Failed to create Jar file …/caches/jars-9/…`, unrelated to the plugin. The
  source comment claims theoretical 7.3+ compatibility; treat that as unverified.
- Gradle 8.3/8.4 need a JDK ≤ 21 daemon; they reject JDK 25 outright.
- Configuration cache is **not** enabled here, and the plugin does its work eagerly during
  configuration.

## 8. CI and release

| Workflow | Trigger | What it does |
|---|---|---|
| `.github/workflows/build.yml` | `push` to `main` + every `pull_request` | JDK 21 (temurin, gradle cache) → `./gradlew build` → publishes the JUnit XML as a check. The `pull_request` trigger matters: the repo takes PRs from forks, and a fork push never fires `on: push` in the upstream repo. |
| `.github/workflows/publish.yml` | push of any **tag** | JDK 21 → appends `gradle.publish.key/secret` from repo secrets to `gradle.properties` → `./gradlew publishPlugins` |

Version lives in `gradle.properties` (a `-SNAPSHOT` between releases). `net.researchgate.release`
drives the flow: `./gradlew release` un-snapshots, commits, tags, bumps to the next snapshot, and
requires `main`. Pushing the resulting tag is what publishes to the portal. Latest published
version: **0.2.0**. Dependabot bumps Gradle deps and Actions daily.

Never run `release` or `publishPlugins` from an agent session — both are outward-facing.

## 9. Gotchas

| Symptom | Cause |
|---|---|
| Repository silently unauthenticated, URL ends in `.on.aws` | `isCodeArtifactUri` requires `.amazonaws.`; dualstack endpoints are not auto-detected. Use the explicit `codeartifact(url)` helper, which accepts them. |
| `Not a valid CodeArtifact repository URL: …` | Host does not match `{domain}-{owner}.d.codeartifact.{region}.…` — e.g. a missing `-{owner}`, or a VPC endpoint. Fails the build by design. |
| `Unresolved reference 'codeartifact'` | Missing `import ai.clarity.codeartifact.codeartifact` in a `.kts` file (`CodeArtifactCredentials` needs its own import). |
| `Could not find method codeartifact()` inside `pluginManagement` | The block runs before the plugin is applied. Use `maven { url = … }` there. |
| Wrong profile used by `codeartifact(url)` | With no profile it falls back to `"default"`; `codeartifact.profile` is ignored on that path, though build-wide service credentials are honoured. |
| `Incomplete CodeArtifact service credentials: …` | Exactly one of `codeartifact.accessKeyId` / `codeartifact.secretAccessKey` is set, in any of the three sources. Set both, or unset both. |
| `The codeartifact.X Gradle property takes precedence over the codeartifact.X system property` | A plain `gradle.properties` entry is shadowing a `-D` / `systemProp.` value. Override with `-P` instead, or drop the plain entry. |
| A `-D` override stopped working after upgrading the plugin | Same cause as the row above: the plain Gradle property form is now read, and it outranks the system property. |
| `Timeout waiting to lock journal cache` | Gradle running inside the Claude Code sandbox. Disable the sandbox. |
| Credentials not injected on a CodeArtifact URL | The repository already had a username or password; the plugin skips those. |

## 10. README discrepancies (as of 2026-09-01)

One left: dualstack `.on.aws` endpoints are undocumented and not auto-detected (see §9).

Four earlier defects are fixed: the stale version pins in the examples, the false "the helper is
not available in `settings.gradle(.kts)`" note, the missing Kotlin imports in the
`codeartifact(...)` snippets, and the resolution-order list that did not say steps 4-5 skip the
helper path.

Everything else in the README (automatic detection, publishing repositories, the profile
mechanisms, the service-account credentials, the `?profile=` stripping, the `gradle.properties` +
CI-override pattern) was reproduced and holds.
