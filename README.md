# Codeartifact Gradle Plugin
![Static Badge](https://img.shields.io/badge/Minimum-8.4-green?style=plastic&logo=gradle)

Gradle plugin which authenticates against [AWS CodeArtifact](https://aws.amazon.com/es/codeartifact/) using your local
credentials to obtain
the token.

The plugin automatically detects any Maven repository whose URL matches a CodeArtifact endpoint and injects the
appropriate credentials — no extra configuration needed.

## Usage

The plugin can be applied to either your project's `build.gradle(.kts)` file or to your `settings.gradle(.kts)` file.

### Project configuration

Apply the plugin to your `build.gradle.kts` (Kotlin DSL) or `build.gradle` (Groovy DSL) to automatically configure repositories in thatproject.

#### Kotlin DSL

##### For dependencies

In your `build.gradle.kts` file:

```kotlin
plugins {
  id("ai.clarity.codeartifact") version "0.2.0"
}

repositories {
  maven {
    url = uri("https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/")
  }
}
```

##### For publishing

In your `build.gradle.kts` file:

```kotlin
plugins {
  id("ai.clarity.codeartifact") version "0.2.0"
}

publishing {
  repositories {
    maven {
      url = uri("https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/")
    }
  }
}
```

#### Groovy DSL

##### For dependencies

In your `build.gradle` file:

```groovy
plugins {
    id 'ai.clarity.codeartifact' version '0.2.0'
}

repositories {
    maven {
        url 'https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/'
    }
}
```

##### For publishing

In your `build.gradle` file:

```groovy
plugins {
    id 'ai.clarity.codeartifact' version '0.2.0'
}

publishing {
    repositories {
        maven {
            url 'https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/'
        }
    }
}
```

### Explicit `codeartifact` method

Instead of relying on automatic detection via `maven { url ... }`, you can use the `codeartifact()` helper method
directly on the `repositories` block. This method accepts the repository URL, an optional profile name or the
[credentials of a service account](#service-account-credentials), and an optional configuration closure/action.

> **Note:** The `codeartifact()` helper can be used in `build.gradle(.kts)`, and in `settings.gradle(.kts)` inside
> `dependencyResolutionManagement`. It cannot be used inside `pluginManagement`, which Gradle evaluates before the
> `plugins { }` block applies this plugin. Use `maven { url ... }` there and rely on automatic detection.

### Kotlin DSL

```kotlin
repositories {
    codeartifact("https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/")

    // With a specific profile:
    codeartifact("https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/", "prod")

    // With a specific profile and additional repository configuration:
    codeartifact("https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/", "prod") {
        name = "myCodeArtifactRepo"
    }

    // With the credentials of a service account instead of a profile:
    codeartifact(
        "https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/",
        CodeArtifactCredentials.of(
            providers.gradleProperty("codeArtifactAccessKeyId").get(),
            providers.gradleProperty("codeArtifactSecretAccessKey").get()
        )
    )
}
```

> **Note:** in `build.gradle.kts` the helper and the credentials class have to be imported:
> `import ai.clarity.codeartifact.codeartifact` and `import ai.clarity.codeartifact.CodeArtifactCredentials`.

### Groovy DSL

```groovy
repositories {
    codeartifact('https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/')

    // With a specific profile:
    codeartifact('https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/', 'prod')

    // With a specific profile and additional repository configuration:
    codeartifact('https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/', 'prod') {
        name = 'myCodeArtifactRepo'
    }

    // With the credentials of a service account instead of a profile:
    codeartifact('https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/', [
            accessKeyId    : providers.gradleProperty('codeArtifactAccessKeyId').get(),
            secretAccessKey: providers.gradleProperty('codeArtifactSecretAccessKey').get()
    ])
}
```

### Settings configuration

Applying the plugin in `settings.gradle.kts` or `settings.gradle` allows you to centralize repository configuration for all projects in the
build, including `pluginManagement` and `dependencyResolutionManagement`.

> **Note:** Inside `pluginManagement` the `codeartifact()` helper is not available, because Gradle evaluates that
> block before the `plugins { }` block applies this plugin. Use the standard `maven { url ... }` approach there and
> rely on automatic detection. Inside `dependencyResolutionManagement` the helper does work.

#### Kotlin DSL

##### For dependencies

In your `settings.gradle.kts` file:

```kotlin
plugins {
  id("ai.clarity.codeartifact") version "0.2.0"
}

dependencyResolutionManagement {
  repositories {
    maven {
      url = uri("https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/")
    }
  }
}
```

##### For plugins

In your `settings.gradle.kts` file:

```kotlin
plugins {
  id("ai.clarity.codeartifact") version "0.2.0"
}

pluginManagement {
  repositories {
    maven {
      url = uri("https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/")
    }
  }
}
```

#### Groovy DSL

##### For dependencies

In your `settings.gradle` file:

```groovy
plugins {
    id 'ai.clarity.codeartifact' version '0.2.0'
}

dependencyResolutionManagement {
    repositories {
        maven {
            url 'https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/'
        }
    }
}
```

##### For plugins

In your `settings.gradle` file:

```groovy
plugins {
    id 'ai.clarity.codeartifact' version '0.2.0'
}

pluginManagement {
    repositories {
        maven {
            url 'https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/'
        }
    }
}
```

## Advanced Usage

If you need a concrete profile for AWS authentication, you have 4 different options:

### 1 – Add the profile name to the repository URL as a query param

#### Kotlin DSL

```kotlin
repositories {
  maven {
    url = uri("https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/?profile=prod")
  }
}
```

#### Groovy DSL

```groovy
repositories {
    maven {
        url 'https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/?profile=prod'
    }
}
```

> **Note:** The query param is used to configure the profile and is automatically removed from the URL in any request to
> AWS.

### 2 – Define the environment variable `AWS_PROFILE` with the profile name you want to use

This plugin uses the AWS SDK for authorization, all
the [standard environment variables](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html) are applicable.

### 3 – Define the environment variable `CODEARTIFACT_PROFILE` with the profile name you want to use

If you need a different profile for CodeArtifact than for the rest of your AWS calls you can use this environment
variable.

### 4 – Define the profile using a system property

If you need a different profile for CodeArtifact and you cannot define an environment variable, you
can define it via a system property.

Using the `gradle.properties` file:

```properties
systemProp.codeartifact.profile=<your profile>
```

Or using the command line:

```bash
gradle -Dcodeartifact.profile=<your profile> ...
```

### Recommended pattern: project-wide default profile with CI/CD override

To give every developer a default profile without any local setup, while still letting CI/CD
override it, commit the default to the project's `gradle.properties` instead of using the
`?profile=` query param:

```properties
# gradle.properties (committed with the project)
systemProp.codeartifact.profile=dev
```

Local builds then use the `dev` profile out of the box, and CI/CD overrides it from the
command line:

```bash
gradle -Dcodeartifact.profile=ci ...
```

Keep in mind:

- The `systemProp.` prefix is required. A plain `codeartifact.profile=dev` entry defines a
  Gradle project property, which the plugin does not read.
- Override with `-D` on the command line. The `CODEARTIFACT_PROFILE` environment variable
  does not take precedence over a system property defined in `gradle.properties`.
- A developer can override the project default for their machine in
  `~/.gradle/gradle.properties`, which takes precedence over the project file.
- Do not combine this pattern with `?profile=` in the repository URL: the query param has
  the highest precedence and would defeat the override.

## Service account credentials

On CI/CD, or on any machine without an `~/.aws/credentials` file, the plugin can authenticate with the static access
keys of a service account — an IAM user or a set of temporary credentials — instead of an AWS profile.

### For the whole build

Configure the access key id and the secret access key. Each value is read from its system property first, and from its
environment variable second:

| Value             | System property                | Environment variable             | Required                       |
|-------------------|--------------------------------|----------------------------------|--------------------------------|
| Access key id     | `codeartifact.accessKeyId`     | `CODEARTIFACT_ACCESS_KEY_ID`     | Yes                            |
| Secret access key | `codeartifact.secretAccessKey` | `CODEARTIFACT_SECRET_ACCESS_KEY` | Yes                            |
| Session token     | `codeartifact.sessionToken`    | `CODEARTIFACT_SESSION_TOKEN`     | Only for temporary credentials |

In a CI/CD pipeline, export them from the secret store of your platform:

```bash
export CODEARTIFACT_ACCESS_KEY_ID=<your access key id>
export CODEARTIFACT_SECRET_ACCESS_KEY=<your secret access key>
```

On a developer machine, declare them in `~/.gradle/gradle.properties`, which lives outside the project:

```properties
systemProp.codeartifact.accessKeyId=<your access key id>
systemProp.codeartifact.secretAccessKey=<your secret access key>
```

They then apply to every CodeArtifact repository of the build, including the ones declared in `settings.gradle(.kts)`,
and they take precedence over the `codeartifact.profile` / `CODEARTIFACT_PROFILE` configuration.

> **Warning:** never commit these values to the project `gradle.properties`. There is deliberately no `?accessKeyId=`
> query param either, because the repository URL ends up in build scans, caches and logs. The plugin only writes a
> masked access key id (`AKIA************MPLE`) to the build log, never the secret.

Setting only one of the two required values fails the build with an explicit message, instead of silently falling back
to another set of credentials.

### For a single repository

When repositories belong to different AWS accounts, pass the credentials to the `codeartifact()` helper. Read the
values from Gradle properties or from the environment rather than hardcoding them in the build script.

#### Kotlin DSL

```kotlin
import ai.clarity.codeartifact.CodeArtifactCredentials
import ai.clarity.codeartifact.codeartifact

repositories {
    codeartifact(
        "https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/",
        CodeArtifactCredentials.of(
            providers.gradleProperty("codeArtifactAccessKeyId").get(),
            providers.gradleProperty("codeArtifactSecretAccessKey").get()
        )
    ) {
        name = "myCodeArtifactRepo"
    }
}
```

#### Groovy DSL

```groovy
repositories {
    codeartifact('https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/', [
            accessKeyId    : providers.gradleProperty('codeArtifactAccessKeyId').get(),
            secretAccessKey: providers.gradleProperty('codeArtifactSecretAccessKey').get()
            // add a sessionToken entry only for temporary credentials
    ]) {
        name = 'myCodeArtifactRepo'
    }
}
```

> **Note:** the `codeartifact()` helper also works in `settings.gradle(.kts)` inside
> `dependencyResolutionManagement`, but not inside `pluginManagement`. Use the build-wide configuration above for
> the repositories declared there.

## Credentials resolution order

The plugin uses the configuration closest to the repository, and prefers service account credentials over profiles at
the same level:

1. Credentials passed to the `codeartifact()` helper
2. Profile passed to the `codeartifact()` helper, or `?profile=` query parameter in the repository URL
3. `codeartifact.accessKeyId` and `codeartifact.secretAccessKey`, each read from the system property first and from the
   matching `CODEARTIFACT_*` environment variable second
4. `codeartifact.profile` Java system property
5. `CODEARTIFACT_PROFILE` environment variable
6. The AWS SDK default credentials provider chain (`AWS_PROFILE`, `AWS_ACCESS_KEY_ID`, instance roles, …)

> **Note:** steps 4 and 5 only apply to the repositories detected automatically. A repository declared with the
> `codeartifact()` helper and no explicit profile falls back to the `default` profile instead, so `codeartifact.profile`
> and `CODEARTIFACT_PROFILE` have no effect on it. Pass the profile to the helper if you need another one.

The incomplete-configuration check on step 3 runs only when steps 1 and 2 did not already settle the authentication:
with a `?profile=` in the URL, or a profile passed to the helper, a half-configured pair of service credentials is
ignored rather than reported.

## License

This project is licensed under the [Apache License 2.0](LICENSE).
