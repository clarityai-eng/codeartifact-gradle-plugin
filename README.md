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
  id("ai.clarity.codeartifact") version "0.0.13"
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
  id("ai.clarity.codeartifact") version "0.0.13"
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
    id 'ai.clarity.codeartifact' version '0.0.13'
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
    id 'ai.clarity.codeartifact' version '0.0.13'
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
directly on the `repositories` block. This method accepts the repository URL, an optional profile name, and an optional
configuration closure/action.

> **Note:** The `Explicit codeartifact method` can only be used inside the `build.gradle` or `build.gradle.kts` files.

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
}
```

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
}
```

### Settings configuration

Applying the plugin in `settings.gradle.kts` or `settings.gradle` allows you to centralize repository configuration for all projects in the
build, including `pluginManagement` and `dependencyResolutionManagement`.

> **Note:** The `codeartifact` helper method is **NOT available** in `settings.gradle(.kts)`. You must use the standard `maven { url ... }` approach for automatic detection instead.

#### Kotlin DSL

##### For dependencies

In your `settings.gradle.kts` file:

```kotlin
plugins {
  id("ai.clarity.codeartifact") version "0.0.13"
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
  id("ai.clarity.codeartifact") version "0.0.13"
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
    id 'ai.clarity.codeartifact' version '0.0.13'
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
    id 'ai.clarity.codeartifact' version '0.0.13'
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

## Profile resolution order

The profile is resolved in the following order of precedence:

1. `?profile=` query parameter in the repository URL
2. `codeartifact.profile` Java system property
3. `CODEARTIFACT_PROFILE` environment variable
4. `AWS_PROFILE` environment variable (handled by the AWS SDK)
5. Falls back to `default`

## License

This project is licensed under the [Apache License 2.0](LICENSE).
