# AGENTS.md

Operating manual for AI agents working on this repository.

## What this is

`codeartifact-gradle-plugin` — an **independent open-source Gradle plugin** (Apache-2.0)
published to the Gradle Plugin Portal as **`ai.clarity.codeartifact`**.

It authenticates against AWS CodeArtifact with the local AWS credential chain, then injects
`username=aws` / `password=<CodeArtifact token>` into every Maven repository whose URL looks
like a CodeArtifact endpoint. No task to run, no extension to configure: it works at
configuration time.

**It is a general-purpose library.** Keep it generic — no Clarity-specific assumptions, no
internal hostnames, domains, account ids, profiles or Jira references in code, tests or docs.
Use the AWS documentation placeholders (`my-domain-111122223333`, `us-west-2`) as the tests do.

## Stack

| | |
|---|---|
| Languages | Kotlin 2.4.10 (plugin/DSL) + Java (URL/token/AWS layer), same package |
| JVM | Gradle toolchain **21**; a newer local JDK is fine (verified on JDK 25 launcher) |
| Build | Gradle **9.7.1** via wrapper, Kotlin DSL, version catalog `gradle/libs.versions.toml` |
| AWS | `software.amazon.awssdk` BOM — `codeartifact`, `sts`, `sso`, `ssooidc` |
| Tests | JUnit 5 + AssertJ + MockK; Gradle TestKit for `functionalTest` |
| Release | `net.researchgate.release` + `com.gradle.plugin-publish`, GitHub Actions |
| Lint/format | **none configured** — match surrounding style by hand |

## Commands

All verified on this checkout (`main`, 2026-09-01).

```bash
./gradlew build            # compile + unit tests + functionalTest + validatePlugins
./gradlew test             # 44 unit tests
./gradlew functionalTest   # 71 TestKit tests across Gradle 8.4, 8.7, 8.14, 9.1.0, 9.4.0
./gradlew publishToMavenLocal   # for trying the plugin from a scratch project
./gradlew tasks --all
```

`./gradlew build` from clean: ~1m30s (`functionalTest` dominates — it boots five Gradle
distributions). Tests are heavily `UP-TO-DATE`-cached; add `--rerun-tasks` when you need proof
they actually ran.

Do **not** run `./gradlew release` or `publishPlugins` — they mutate git (tag + version commits,
`requireBranch = "main"`) and publish publicly. Releases happen by pushing a tag; see
`docs/ONBOARDING.md`.

### Sandbox

Gradle cannot run inside the Claude Code sandbox (it hangs on
`Timeout waiting to lock journal cache`, because the sandbox PID namespace breaks lock-owner
validation). Run every `./gradlew` invocation with the sandbox disabled.

## Layout

```
src/main/kotlin/ai/clarity/codeartifact/
  ClarityCodeArtifactGradlePlugin.kt   # ENTRY POINT (implementationClass); also the
                                       #   RepositoryHandler.codeartifact() Kotlin extension
  CodeArtifactProjectPlugin.kt         # Project target: repositories + publishing repositories
  CodeArtifactSettingsPlugin.kt        # Settings target: pluginManagement + DRM repositories
  CodeartifactRepositoryConfigurer.kt  # shared logic: detect, resolve profile, inject creds
src/main/java/ai/clarity/codeartifact/
  CodeArtifactToken.java               # Gradle BuildService, caches tokens by "profile@url"
  TokenFactory.java                    # AWS SDK GetAuthorizationToken call
  CodeArtifactUrl.java                 # host parsing -> domain / owner / region
  URIBuilder.java                      # immutable-ish URI query-param editing
src/test/{kotlin,java}/...             # unit tests (ProjectBuilder + MockK on TokenFactory)
src/functionalTest/kotlin/...          # TestKit tests, incl. a stubbed CodeArtifact endpoint
.github/workflows/build.yml            # CI: JDK 21, ./gradlew build, on every push
.github/workflows/publish.yml          # Release: on tag push, ./gradlew publishPlugins
```

## Conventions

- **Indentation 2 spaces**, no tabs. Soft line limit ~140 columns (current max is 138).
- Every source file carries the Apache-2.0 header block already present in siblings.
- Kotlin classes that are not part of the public surface are `internal`.
- **Conventional Commits without a ticket scope**: `feat:`, `fix:`, `docs:`, `test:`, `ci:`,
  `build:`, `refactor:`, `chore(deps):`. This repo is not tied to any Jira board — do **not**
  add `IND-`/`IE-` scopes here, even though the global instructions ask for them elsewhere.
- Trunk-based: `main` is the only long-lived branch, PRs into it, Dependabot handles bumps.
- Tests use `// Given: / // When: / // Then:` comments and AssertJ (`assertThat`).
  Unit tests fake AWS with `mockkStatic(TokenFactory::class)`; functional tests either expect
  `buildAndFail()` (no AWS creds) or point the SDK at a local stub via
  `AWS_ENDPOINT_URL_CODEARTIFACT`.
- Adding a Gradle version to the compatibility matrix = one entry in
  `ClarityCodeartifactPluginFunctionalTest.gradleVersions()`.

## Behaviour you must not break

Established by the test suite and verified end-to-end (see `docs/ONBOARDING.md` for the
experiments):

1. Non-CodeArtifact repositories are left completely untouched (credentials stay `null`).
2. A CodeArtifact repository that **already has credentials** is skipped.
3. `?profile=<name>` is read from the URL and then **stripped** from the final repository URL.
4. Profile precedence: `?profile=` → `-Dcodeartifact.profile` / `systemProp.codeartifact.profile`
   → `CODEARTIFACT_PROFILE` → *null* (AWS default chain, which honours `AWS_PROFILE`).
5. Tokens are fetched **once per `profile@url`** and shared through a Gradle `BuildService`.
6. A URL that looks like CodeArtifact but cannot be parsed **fails the build** with
   `Not a valid CodeArtifact repository URL: … (expected format: …)`.

## Known gaps — do not document these as working

- **Dualstack `.on.aws` endpoints are not auto-detected.** `CodeArtifactUrl` accepts them, but
  `CodeartifactRepositoryConfigurer.isCodeArtifactUri` requires `.amazonaws.`, so a
  `maven { url = ".../codeartifact.<region>.on.aws/..." }` silently gets no credentials. The
  explicit `codeartifact(...)` helper *does* work with dualstack URLs.
- **VPC endpoints are unsupported by design** (domain/owner live in the path, not the host).
- **`codeartifact(url)` hardcodes the profile `"default"`** — it ignores
  `codeartifact.profile`, `CODEARTIFACT_PROFILE` and `AWS_PROFILE`. Pass the profile explicitly.
- **The Kotlin DSL helper needs `import ai.clarity.codeartifact.codeartifact`.** The README's
  Kotlin examples omit it and therefore do not compile.
- **No linter or formatter** is wired into the build; `./gradlew check` will not catch style.

## README caveats

`README.md` is mostly accurate but has three defects — fix them only if asked:

1. All examples pin `version "0.1.1"`; the latest published version is **0.1.2**
   (working version here is `0.1.3-SNAPSHOT`).
2. It claims the `codeartifact` helper is "**NOT available** in `settings.gradle(.kts)`".
   Verified false: it works inside `dependencyResolutionManagement` in both DSLs. It genuinely
   does *not* work inside `pluginManagement`, because that block is evaluated before `plugins {}`
   applies the plugin.
3. The Kotlin `codeartifact(...)` snippets are missing the required import.
