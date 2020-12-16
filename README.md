# Codeartifact gradle plugin

Gradle plugin which authenticates against [AWS CodeArtifact](https://aws.amazon.com/es/codeartifact/) using your local credentials to obtain
the token.

## Usage

In your build.gradle file:

```
plugins {
    id 'ai.clarity.codeartifact' version '0.0.10'
}

repositories {
    maven {
        url 'https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/'
    }
}

publishing {
    repositories {
        maven {
            url 'https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/'
        }
    }
}
```

### Advanced Usage

If you need a concrete profile for AWS authentication you have 3 options:

#### Add the profile name to the repository url as a query param:

```
repositories {
    maven {
        url 'https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/?profile=prod'
    }
}

```

The query param is used to configure the profile and removed from the url in any request to AWS.

#### Define the env var `CODEARTIFACT_PROFILE` with the profile name you want to use

#### Define the env var `AWS_PROFILE` with the profile name you want to use