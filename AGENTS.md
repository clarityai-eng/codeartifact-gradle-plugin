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
| Tests | JUnit 5 + AssertJ + MockK; Gradle TestKit for `functionalTest`; Testcontainers + AWS Emulator for `integrationTest` |
| Release | `net.researchgate.release` + `com.gradle.plugin-publish`, GitHub Actions |
| Lint/format | no linter or formatter; `javadoc -Werror` is the only automated gate — match surrounding style by hand |

## Commands

All verified on this checkout (2026-11-17).

```bash
./gradlew build            # compile + unit, functional and integration tests + validatePlugins
./gradlew test             # 105 unit tests
./gradlew functionalTest   # 101 TestKit tests across Gradle 8.4, 8.7, 8.14, 9.1.0, 9.4.0
./gradlew integrationTest  # 12 tests against AWS Emulator, in Docker
./gradlew publishToMavenLocal   # for trying the plugin from a scratch project
./gradlew tasks --all
```

`./gradlew build` from clean: ~4m (`functionalTest` dominates — it boots five Gradle
distributions). Tests are heavily `UP-TO-DATE`-cached; add `--rerun-tasks` when you need proof
they actually ran.

`integrationTest` (part of `check`, so of `build`) needs a running Docker daemon. It builds the
AWS Emulator image from `src/integrationTest/resources/aws-emulator/Dockerfile`, which downloads a
~190 MB package, and starts one container for the whole run. Without Docker, run
`./gradlew build -x integrationTest`.

Do **not** run `./gradlew release` or `publishPlugins` — they mutate git (tag + version commits,
`requireBranch = "main"`) and publish publicly. Releases happen by pushing a tag; see
`docs/ONBOARDING.md`.

### Sandbox

Gradle cannot run inside the Claude Code sandbox (it hangs on
`Timeout waiting to lock journal cache`, because the sandbox PID namespace breaks lock-owner
validation). Run every `./gradlew` invocation with the sandbox disabled. `integrationTest` also
needs the Docker socket, which the sandbox blocks.

## Layout

```
src/main/kotlin/ai/clarity/codeartifact/
  ClarityCodeArtifactGradlePlugin.kt   # ENTRY POINT (implementationClass); also the
                                       #   RepositoryHandler.codeartifact() Kotlin extension
  CodeArtifactProjectPlugin.kt         # Project target: repositories + publishing repositories
  CodeArtifactSettingsPlugin.kt        # Settings target: pluginManagement + DRM repositories
  CodeartifactRepositoryConfigurer.kt  # shared logic: detect, resolve profile, inject creds
  CodeArtifactAuthenticator.kt         # picks credentials vs profile for one repository
  CodeArtifactCredentialsResolver.kt   # build-wide service credentials, read via SettingLookup
  SettingLookup.kt                     # one codeartifact.* setting from Gradle property /
                                       #   system property / env, plus the shadowing warning
src/main/java/ai/clarity/codeartifact/
  CodeArtifactToken.java               # Gradle BuildService, caches tokens per auth+url
  CodeArtifactCredentials.java         # static service-account keys: masking, redaction, cache key
  TokenFactory.java                    # AWS SDK GetAuthorizationToken call
  CodeArtifactUrl.java                 # host parsing -> domain / owner / region
  URIBuilder.java                      # immutable-ish URI query-param editing
src/test/{kotlin,java}/...             # unit tests (ProjectBuilder + MockK on TokenFactory)
src/functionalTest/kotlin/...          # TestKit tests that need no CodeArtifact endpoint
src/integrationTest/kotlin/...         # TokenFactory and TestKit tests against AWS Emulator;
                                       #   AwsEmulator.kt starts and wires the shared container
src/integrationTest/resources/aws-emulator/Dockerfile  # the emulator image (AWS publishes none)
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
  Unit tests fake AWS with `mockkStatic(TokenFactory::class)`; functional tests expect
  `buildAndFail()` (no AWS creds) and assert which credentials were chosen from the `--info` log.
  Anything that needs a real token response goes to `integrationTest`, against AWS Emulator.
- Integration tests reach the emulator only through `AwsEmulator`: in-process clients through JVM
  system properties, TestKit builds through `AwsEmulator.buildEnvironment()` plus
  `AwsEmulator.daemonTrustStoreProperty` in their `gradle.properties`. The emulator checks SigV4
  signatures, so a scenario proves which credentials signed the request by offering the accepted
  keys through the source under test and rejected keys through the source it must win over.
  Repository URLs use the account `000000000000`, the only one the emulator acts as.
- Adding a Gradle version to the compatibility matrix = one entry in
  `ClarityCodeartifactPluginFunctionalTest.gradleVersions()`.

## Behaviour you must not break

Established by the test suite and verified end-to-end (see `docs/ONBOARDING.md` for the
experiments):

1. Non-CodeArtifact repositories are left completely untouched (credentials stay `null`).
2. A CodeArtifact repository that **already has credentials** is skipped.
3. `?profile=<name>` is read from the URL and then **stripped** from the final repository URL.
   Both public host shapes are detected and parsed: the ipv4 one
   (`{domain}-{owner}.d.codeartifact.{region}.amazonaws.com`) and the dualstack one
   (`{domain}-{owner}.codeartifact.{region}.on.aws`, which has **no** `.d.` segment). A host that
   mixes the two is rejected.
4. Credentials precedence, closest-to-the-repository first: credentials passed to
   `codeartifact()` → `?profile=` or a profile passed to `codeartifact()` →
   `codeartifact.accessKeyId`/`secretAccessKey` → `codeartifact.profile` → *null* (AWS default
   chain, which honours `AWS_PROFILE`). The `codeartifact.profile` step applies to
   automatically detected repositories only; the `codeartifact()` helper falls back to the
   `default` profile instead.
   Every `codeartifact.*` setting is read by `SettingLookup` from three sources in order:
   Gradle property (plain `gradle.properties` entry or `-P`) → system property (`systemProp.`
   or `-D`) → `CODEARTIFACT_*` environment variable. The first source that holds the setting
   wins even when blank, and a blank value resolves to `null`. A Gradle property shadowing a
   differently-valued system property — or, when no system property is set, a differently-valued
   environment variable — logs a warning, once per setting.
5. Tokens are fetched **once per authentication + url** and shared through a Gradle
   `BuildService`. Profile entries and service-credential entries never share a cache slot, and
   the credentials half of the key is a SHA-256 digest so no secret is held in clear.
6. A URL that looks like CodeArtifact but cannot be parsed **fails the build** with
   `Not a valid CodeArtifact repository URL: … (expected format: …)`.
7. **No secret ever reaches the build log.** Only a masked access key id
   (`AKIA************MPLE`) is logged, `CodeArtifactCredentials.toString()` is redacted, and a
   half-configured credential pair fails with `Incomplete CodeArtifact service credentials`
   rather than falling back silently. Verified at `--debug --stacktrace`: 0 occurrences of the
   secret in 6249 log lines, and on every run by an integration test whose token request the
   emulator refuses.

## Known gaps — do not document these as working

- **VPC endpoints are unsupported by design** (domain/owner live in the path, not the host).
- **`codeartifact(url)` with no profile falls back to the `default` profile** — it honours the
  build-wide service credentials, but still ignores `codeartifact.profile`,
  `CODEARTIFACT_PROFILE` and `AWS_PROFILE`, unlike automatic detection. Pass the profile
  explicitly. Known inconsistency, deliberately left alone so far.
- **The Kotlin DSL needs explicit imports**: `ai.clarity.codeartifact.codeartifact` for the
  helper and `ai.clarity.codeartifact.CodeArtifactCredentials` for the credentials class.
- **The `codeartifact()` helper does not work inside `pluginManagement`** (that block runs
  before `plugins { }` applies the plugin). It does work inside
  `dependencyResolutionManagement`, in both DSLs.
- **At `--debug` the full access key id reaches the log** (never the secret). The AWS SDK's
  default HTTP client, Apache HttpClient 5 (`apache5-client`), logs the `Authorization` header,
  whose SigV4 credential scope carries the access key id. Found by the integration tests,
  2026-11-17, SDK 2.57.8.
- **The integration tests run against whatever AWS Emulator is current.** AWS publishes no
  versioned package, only `LTS/latest`, and releases several times a day, so a red
  `integrationTest` with no change on our side may be the emulator. Each run prints the version
  it used (`AWS Emulator (Version: …)` in the standard output of the test report).
- **No linter or formatter** is wired into the build; `./gradlew check` will not catch style. The one
  automated gate is `javadoc -Werror`: every public member of the four Java classes is documented, and
  `javadoc` runs as part of `build`, so an undocumented one fails the build. Kotlin is not covered — there
  is no Dokka.

## README caveats

None known. The examples pin the current release; re-check them after every version bump.
