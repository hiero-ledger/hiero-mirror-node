# Web3 Custom Buildpack (libsodium-enabled)

## 📌 Overview

This project defines a **custom Cloud Native Buildpacks (CNB) builder** used by the `web3` module to support:

- libsodium (required by LazySodium / JNA)
- GraalVM native image builds

The default Paketo builders do **not reliably support libsodium** in native images due to:

- Missing system libraries in build/run images
- Runtime extraction issues (SharedLibraryLoader, temp dirs, etc.)

This custom builder solves those issues by **baking libsodium directly into both build and run images**.

---

## ❗ Why this is needed

The `web3` service depends on:

- com.goterl.lazysodium

This library:

- Dynamically loads libsodium
- Expects it to be available on the system (or bundled correctly which isn't possible in native image)

---

## 🏗️ Architecture

### Build Image

web3-build-noble-libsodium

- Contains:
  - GraalVM (via Paketo)
  - libsodium
- Used during native-image compilation

### Builder

web3-builder-libsodium

- Combines:
  - Paketo Java buildpacks
  - Native image buildpack
  - Custom build/run images

---

## 📦 Build & Publish

### Prerequisites

```
brew install buildpacks/tap/pack
docker login
```

### 1. Set version tag

```
export LIBSODIUM_TAG=20260317-3
```

### 2. Build build image

```
docker build \
  -f web3/buildpack/build.Dockerfile \
  -t docker.io/jesseswirldslabs/web3-build-noble-libsodium:${LIBSODIUM_TAG} \
  web3
```

### 3. Build run image

```
docker build \
  -f web3/buildpack/run.Dockerfile \
  -t docker.io/jesseswirldslabs/web3-run-noble-libsodium:${LIBSODIUM_TAG} \
  web3
```

### 4. Push images

```
docker push docker.io/jesseswirldslabs/web3-build-noble-libsodium:${LIBSODIUM_TAG}
docker push docker.io/jesseswirldslabs/web3-run-noble-libsodium:${LIBSODIUM_TAG}
```

### 5. Update builder.toml

```
[stack]
  build-image = "docker.io/jesseswirldslabs/web3-build-noble-libsodium:${LIBSODIUM_TAG}"
  run-image = "docker.io/jesseswirldslabs/web3-run-noble-libsodium:${LIBSODIUM_TAG}"
```

### 6. Create and publish builder

```
pack builder create docker.io/jesseswirldslabs/web3-builder-libsodium:${LIBSODIUM_TAG} \
  --config web3/buildpack/builder.toml \
  --publish
```

---

## 🚀 Using the builder in Gradle

```
if (project.name == "web3") {
    builder.set("docker.io/jesseswirldslabs/web3-builder-libsodium:20260317-2")
}
```

---

## 🚧 Future improvements

- Move libsodium logic fully into web3 module
- Automate builder publishing via CI
