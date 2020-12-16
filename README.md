# Codeartifact gradle plugin

Gradle plugin which authenticates against [AWS CodeArtifact](https://aws.amazon.com/es/codeartifact/) using your local credentials to obtain
the token.

## Usage

In your build.gradle file:

```
plugins {
    id 'ai.clarity.codeartifact' version '0.0.8'
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

If you need a concrete profile for AWS authentication you should add it to the repository url as a query param:

```
repositories {
    maven {
        url 'https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/?profile=prod'
    }
}

```

The query param is used to configure the profile and removed from the url in any request to AWS.