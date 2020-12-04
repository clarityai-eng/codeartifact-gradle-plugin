#Codeartifact gradle plugin

Gradle plugin which authenticates against [AWS CodeArtifact](https://aws.amazon.com/es/codeartifact/) using your local credentials to obtain the token.


## Usage

In your build.gradle file:

```
plugins {
    id 'ai.clarity.codeartifact' version '0.0.1'
}

repositories {
    maven {
        url 'https://domain-id.d.codeartifact.eu-central-1.amazonaws.com/maven/repository/'
        ai.clarity.codeartifact.ClarityCodeartifactPlugin.setAuthorizationToken(owner, "mgmt")
    }
}

```
