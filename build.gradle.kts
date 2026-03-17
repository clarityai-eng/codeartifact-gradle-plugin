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

  // Use the Kotlin Test integration.
  testImplementation(libs.kotlin.tests)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.assertj.core)
  testImplementation(libs.mockk)

  testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
  // Define the plugin
  website = "https://github.com/clarityai-eng/codeartifact-gradle-plugin"
  vcsUrl = "https://github.com/clarityai-eng/codeartifact-gradle-plugin"
  description = "Gradle plugin to login easily to AWS CodeArtifact"
  plugins {
    register("clarityCodeartifact") {
      id = "ai.clarity.codeartifact"
      implementationClass = "ai.clarity.codeartifact.ClarityCodeArtifactGradlePlugin"
      displayName = "Clarity CodeArtifact Plugin"
      description = "Gradle plugin to login easily to AWS CodeArtifact"
      tags = listOf("aws", "codeartifact")
    }
  }
}

// Add a source set for the functional test suite
val functionalTestSourceSet = sourceSets.create("functionalTest") {
}

configurations["functionalTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["functionalTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

// Add a task to run the functional tests
val functionalTest by tasks.registering(Test::class) {
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


release {
  git {
    requireBranch = "main"
  }
}