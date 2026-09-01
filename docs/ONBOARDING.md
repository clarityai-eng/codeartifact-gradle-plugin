# Onboarding

Everything here was executed against this checkout (`main`, 2026-09-01) — no claim is copied
from `README.md` without verification. The last section lists what the README gets wrong.

## 1. What the plugin does

Consumers declare an ordinary Maven repository pointing at an AWS CodeArtifact endpoint. At
Gradle **configuration time** the plugin:

1. spots repositories whose URL matches `.+\.codeartifact\..+\.amazonaws\..+` (case-insensitive);
2. resolves which AWS profile to use;
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
| AWS | a resolvable credential chain — profile, SSO, env vars, instance role… |

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
                                     │    detect ▸ profile ▸ token ▸ creds│
                                     └───────────────┬────────────────────┘
                                                     │
                       ┌─────────────────────────────▼──────────────────┐
                       │ CodeArtifactToken  (Gradle BuildService)        │
                       │   ConcurrentHashMap<"profile@url", token>       │
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
| `CodeartifactRepositoryConfigurer.kt` | The only place detection, profile resolution and credential injection live. |

### Support classes (Java)

- **`CodeArtifactToken`** — `BuildService<None>`, registered as `"codeartifact-token"`, caching
  by `profile + "@" + url`. One token per profile/URL pair per build, shared across projects.
- **`TokenFactory`** — builds a `CodeartifactClient` for the URL's region. If `profileName` is
  non-null it forces `ProfileCredentialsProvider.create(profile)`; if null it leaves the AWS
  default credential chain in place (which is how `AWS_PROFILE`, SSO and instance roles work).
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
plugins { id("ai.clarity.codeartifact") version "0.1.2" }
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

Two traps:

- **Without the Kotlin import you get `Unresolved reference 'codeartifact'`** (a fully-qualified
  `ai.clarity.codeartifact.codeartifact(...)` call does not work either — it is an extension
  function).
- **The profile defaults to the literal `"default"`**, not to the resolution chain below. With
  `CODEARTIFACT_PROFILE=dev` set, `codeartifact(url)` still logs
  `in profile default`. Pass the profile explicitly if you need another one.

Both work in `dependencyResolutionManagement` inside `settings.gradle(.kts)` too, but **not**
in `pluginManagement`: that block is evaluated before `plugins { }` applies the plugin, so the
method does not exist yet (`Could not find method codeartifact()`).

## 5. Profile resolution

Verified order, highest precedence first:

| # | Source | Verified behaviour |
|---|---|---|
| 1 | `?profile=<name>` in the repository URL | wins over everything; stripped from the final URL |
| 2 | `-Dcodeartifact.profile=<name>` / `systemProp.codeartifact.profile=<name>` in `gradle.properties` | command line overrides the properties file |
| 3 | `CODEARTIFACT_PROFILE` env var | used when 1 and 2 are absent |
| 4 | nothing → profile is `null` | the AWS SDK default chain runs, so `AWS_PROFILE`, SSO caches and instance roles apply |

`codeartifact.profile` must carry the `systemProp.` prefix in `gradle.properties`; a plain
`codeartifact.profile=dev` becomes a Gradle project property, which the plugin never reads.

Recommended pattern for teams: commit `systemProp.codeartifact.profile=dev` and let CI override
with `-Dcodeartifact.profile=ci`. Do not mix it with `?profile=`, which would defeat the override.

## 6. Testing

```bash
./gradlew test --rerun-tasks            # 44 tests, all green
./gradlew functionalTest --rerun-tasks  # 71 tests, all green
./gradlew build                         # both + validatePlugins; ~1m30s from clean
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
./gradlew publishToMavenLocal            # publishes 0.1.3-SNAPSHOT
# stub server returning {"authorizationToken":"stub-token"} on any request, e.g. on :18099
# then, in a scratch project:
#   settings.gradle.kts -> pluginManagement { repositories { mavenLocal(); gradlePluginPortal() } }
#   build.gradle.kts    -> plugins { id("ai.clarity.codeartifact") version "0.1.3-SNAPSHOT" }
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
| `.github/workflows/build.yml` | every `push` | JDK 21 (temurin, gradle cache) → `./gradlew build` → publishes the JUnit XML as a check |
| `.github/workflows/publish.yml` | push of any **tag** | JDK 21 → appends `gradle.publish.key/secret` from repo secrets to `gradle.properties` → `./gradlew publishPlugins` |

Version lives in `gradle.properties` (`version=0.1.3-SNAPSHOT`). `net.researchgate.release`
drives the flow: `./gradlew release` un-snapshots, commits, tags, bumps to the next snapshot, and
requires `main`. Pushing the resulting tag is what publishes to the portal. Latest published
version: **0.1.2**. Dependabot bumps Gradle deps and Actions daily.

Never run `release` or `publishPlugins` from an agent session — both are outward-facing.

## 9. Gotchas

| Symptom | Cause |
|---|---|
| Repository silently unauthenticated, URL ends in `.on.aws` | `isCodeArtifactUri` requires `.amazonaws.`; dualstack endpoints are not auto-detected. Use the explicit `codeartifact(url)` helper, which accepts them. |
| `Not a valid CodeArtifact repository URL: …` | Host does not match `{domain}-{owner}.d.codeartifact.{region}.…` — e.g. a missing `-{owner}`, or a VPC endpoint. Fails the build by design. |
| `Unresolved reference 'codeartifact'` | Missing `import ai.clarity.codeartifact.codeartifact` in a `.kts` file. |
| `Could not find method codeartifact()` inside `pluginManagement` | The block runs before the plugin is applied. Use `maven { url = … }` there. |
| Wrong profile used by `codeartifact(url)` | It hardcodes `"default"`; env vars and system properties are ignored on that path. |
| `Timeout waiting to lock journal cache` | Gradle running inside the Claude Code sandbox. Disable the sandbox. |
| Credentials not injected on a CodeArtifact URL | The repository already had a username or password; the plugin skips those. |

## 10. README discrepancies (as of 2026-09-01)

1. Every example pins `version "0.1.1"`; the latest published version is **0.1.2**.
2. "The `codeartifact` helper method is **NOT available** in `settings.gradle(.kts)`" — false.
   It works in `dependencyResolutionManagement` in both DSLs; it fails only in `pluginManagement`.
3. The Kotlin `codeartifact(...)` snippets omit `import ai.clarity.codeartifact.codeartifact`
   and therefore do not compile.
4. Dualstack `.on.aws` endpoints are undocumented and not auto-detected.
5. The "Profile resolution order" section applies to automatic detection only, not to the
   explicit `codeartifact()` helper — the README does not say so.

Everything else in the README (automatic detection, publishing repositories, the four profile
mechanisms, the `?profile=` stripping, the `gradle.properties` + CI-override pattern) was
reproduced and holds.
