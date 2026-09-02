plugins {
  // Apply the Kotlin JVM plugin to add support for Kotlin.
  alias(libs.plugins.kotlin.jvm)

  // Apply Gradle publish plugin (already includes maven-publish and Java Gradle Development Plugin)
  alias(libs.plugins.gradle.plugin.publish)

  // Enabled to run "gradle release" task to generates application release
  alias(libs.plugins.researchgate.release)
}

group = "ai.clarity"

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

repositories {
  // Use Maven Central for resolving dependencies.
  mavenCentral()
}

dependencies {
  // AWS SDK BOM and dependencies
  implementation(platform(libs.aws.bom))
  implementation(libs.aws.codeartifact)
  implementation(libs.aws.sts)
  implementation(libs.aws.sso)
  implementation(libs.aws.ssooidc)

  // Use the Kotlin Test integration.
  testImplementation(libs.kotlin.tests)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.assertj.core)
  testImplementation(libs.mockk)

  testRuntimeOnly(libs.junit.platform.launcher)
}

// Shown on the Gradle Plugin Portal. The portal keeps the description it recorded at the first
// publication, so a change here does not reach the published page on its own: it also needs a
// request at https://github.com/gradle/plugin-portal-requests
val pluginDescription = "Easily use private AWS CodeArtifact repositories from Gradle - for dependencies, plugins and " +
  "publishing. Just apply the plugin: it detects the CodeArtifact repositories declared in your build, including " +
  "those in pluginManagement and dependencyResolutionManagement, and injects the authorization token for you, with " +
  "no extra configuration. Authenticates through an AWS profile, the static access keys of a service account, SSO, " +
  "or the default AWS credential chain."

gradlePlugin {
  // Define the plugin
  website = "https://github.com/clarityai-eng/codeartifact-gradle-plugin"
  vcsUrl = "https://github.com/clarityai-eng/codeartifact-gradle-plugin"
  description = pluginDescription
  plugins {
    register("clarityCodeartifact") {
      id = "ai.clarity.codeartifact"
      implementationClass = "ai.clarity.codeartifact.ClarityCodeArtifactGradlePlugin"
      displayName = "Clarity CodeArtifact Plugin"
      description = pluginDescription
      tags = listOf("aws", "codeartifact", "maven", "authentication", "credentials", "publishing")
    }
  }
}

// Add a source set for the functional test suite
val functionalTestSourceSet = sourceSets.create("functionalTest") {
}

configurations["functionalTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["functionalTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

// Add a task to run the functional tests
val functionalTest = tasks.register<Test>("functionalTest") {
  testClassesDirs = functionalTestSourceSet.output.classesDirs
  classpath = functionalTestSourceSet.runtimeClasspath
  useJUnitPlatform()
}

gradlePlugin.testSourceSets.add(functionalTestSourceSet)

tasks.named<Task>("check") {
  // Run the functional tests as part of `check`
  dependsOn(functionalTest)
}

tasks.named<Test>("test") {
  // Use JUnit Jupiter for unit tests.
  useJUnitPlatform()
}

tasks.javadoc {
  // The javadoc jar is part of the publication, so a missing comment ships to consumers' IDEs. The whole surface is
  // documented; -Werror is what stops it regrowing, and `javadoc` already runs as part of `build`.
  (options as StandardJavadocDocletOptions).addBooleanOption("Werror", true)
}


release {
  git {
    requireBranch = "main"
  }
}