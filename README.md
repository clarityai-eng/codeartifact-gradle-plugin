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

If you need a concrete profile for AWS authentication you have 3 different options:

#### 1 - Add the profile name to the repository url as a query param:

```
repositories {
    maven {
        url 'https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/?profile=prod'
    }
}

```

Note: The query param is used to configure the profile and automatically removed from the url in any request to AWS.

#### 2 - Define the environment var `AWS_PROFILE` with the profile name you want to use

This plugin uses AWS SDK for authorization, all
the [standard environment vars](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html) are applicable.

#### 3 - Define the environment var `CODEARTIFACT_PROFILE` with the profile name you want to use

If you need a different profile for codeartifact than for the rest of AWS calls you can use this environment var. 