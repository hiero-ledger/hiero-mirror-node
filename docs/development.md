# Development Guide

This document outlines the process to set up a development environment for the mirror node. It covers the
required software, environment setup, IDE configurations, project compilation, and how to run tests.

## Prerequisites

To contribute to the mirror node project, you need to install the following software and tools:

### Java 21

Requires **Java 21** or a later version for development.

- **Install Java 21**:

  On Ubuntu and macOS we recommend using [SDKMAN!](https://sdkman.io/) to manage JVM versions:

  ```bash
  curl -s "https://get.sdkman.io" | bash
  sdk install java 21-tem
  ```

- **Verify Installation**:

  ```bash
  java -version
  ```

- **You should see output similar to:**
  ```
  openjdk version "21.0.4" 2024-XX-XX
  ```
  If you are managing multiple Java versions, you can use the sdk tool to switch to Java 21:
  ```bash
  sdk use java 21-tem
  ```

### Docker

Docker is used to manage and run containerized services (such as databases) for the mirror node. To install Docker,
follow the get started guide on [docker.com](https://docs.docker.com/get-started/get-docker/).

## Coding Standards

We follow best practices to ensure that code quality and maintainability remain high. Below are the key coding standards
to follow:

1. **Java Coding Style**: Follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) for
   writing clean and readable Java code.
2. **Kotlin Style**: Follow the [Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html) for
   Kotlin-based files.
3. **Spotless Plugin**: The project uses the Spotless plugin to enforce consistent formatting. You can run this before
   submitting code:
   ```bash
   ./gradlew spotlessApply
   ```

## IDE Setup

We recommend using **IntelliJ IDEA** for development.

### IntelliJ IDEA Setup

1. **Install IntelliJ IDEA**:

   - Download from [IntelliJ IDEA website](https://www.jetbrains.com/idea/).

2. **Set up Project SDK**:

   - Go to `File > Project Structure > Project`.
   - Set **Project SDK** to `Java 21` (or higher).
   - Set **Project language level** to `21 - Record patterns, pattern matching for switch`.

3. **Gradle Configuration**:

   - Ensure **Gradle JVM** is set to **Java 21**: Go to
     `File > Settings > Build, Execution, Deployment > Build Tools > Gradle` and set the **Gradle JVM** to Java 21.

4. **Enable Save Actions**:

   - Go to `File > Settings > Tools > Actions on Save`
   - Enable the following save actions:
     - `Reformat code`: Ensures consistent code style by reformatting code on save.
     - `Optimize imports`: Automatically removes unused imports and arranges them.
     - `Rearrange code`: Arranges code based on predefined rules.
     - `Run code cleanup`: Cleans up unnecessary elements in the code.
     - `Build project`: Automatically builds the project upon saving if needed.

5. **Import Java Code Style**:

   - `Download`
     the Java code file located in the repository at [docs/palantir-style.xml](docs/palantir-style.xml)
   - In IntelliJ, go to `File > Settings > Editor > Code Style`.
   - Click on `Java` under `Code Style`.
   - In the Code Style section for Java, look for an option to import the downloaded code style file. This can
     typically be found by clicking on the `gear icon (⚙️)` or the `Scheme dropdown`.
   - Choose `Import Scheme > IntelliJ IDEA code style XML` or a similar option.
   - `Import` the downloaded Java code style file to ensure consistent formatting across the project.

6. **Enable Docker Integration**:
   - Enable Docker integration in IntelliJ if you are running containerized services directly from the IDE.

## Compiling the Project

This project uses [Gradle](https://gradle.org) for building and managing dependencies. It's not necessary to install
Gradle locally as the project automatically downloads it via the Gradle Wrapper.

1. **Clean and Build the Project**:
   Run the following command to clean and build the project:

   ```bash
   ./gradlew clean build
   ```

   This command will:

   - Clean previous builds.
   - Compile the source code.
   - Download any required dependencies.
   - Run tests.

2. **Compile Specific Subprojects**:
   If you want to build a specific subproject (e.g., `monitor`), run:

   ```bash
   ./gradlew :monitor:build
   ```

## Running Tests

You can run the project’s tests using Gradle.

1. **Run All Tests**:
   To run all the tests, use:

   ```bash
   ./gradlew test
   ```

2. **Run Tests for a Specific Subproject**:
   To run tests for a specific subproject (e.g., `common`):

   ```bash
   ./gradlew :common:test
   ```

3. **Running Specific Tests**:
   You can also run specific test classes or methods:

   ```bash
   ./gradlew test --tests "*YourTestClassName"
   ```

   or:

   ```bash
   ./gradlew test --tests "*YourTestClassName.yourTestMethodName"
   ```

## Docker Integration for Local Development

The mirror node often depends on containerized services such as **PostgreSQL** or **Redis**. These services are
defined in `docker-compose` files within the repository.

1. **Start Docker Services**:
   To start all services needed for local development:

   ```bash
   docker compose up
   ```

2. **Stop Docker Services**:
   To stop the services:

   ```bash
   docker compose down
   ```

## Container Images

For production or deployment, you may need to generate container images.

1. **Build Container Images**:
   You can build container images using Gradle and specify custom properties to control the platform, registry, and tag.

   ```bash
   ./gradlew dockerBuild \
   -PimagePlatform=linux/amd64 \
   -PimageRegistry=docker.io/mydockerid \
   -PimageTag=1.0.0-SNAPSHOT
   ```

2. **Push Container Images**:
   After building the container image, you can push it to the specified image registry to make it available for use in a
   remote Kubernetes environment.

   ```bash
      ./gradlew dockerPush \
      -PimagePlatform=linux/amd64 \
      -PimageRegistry=docker.io \
      -PimageTag=1.0.0-SNAPSHOT
   ```

   Note: Ensure you are logged into the image registry if authentication is required. This command will push the image
   with the specified tag to the registry.
