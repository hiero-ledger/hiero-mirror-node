brew install buildpacks/tap/pack

export LIBSODIUM_TAG=20260317-2

docker build -f web3/buildpack/build.Dockerfile \
-t docker.io/jesseswirldslabs/web3-build-noble-libsodium:${LIBSODIUM_TAG} \
web3

docker build -f web3/buildpack/run.Dockerfile \
-t docker.io/jesseswirldslabs/web3-run-noble-libsodium:${LIBSODIUM_TAG} \
web3

docker push docker.io/jesseswirldslabs/web3-build-noble-libsodium:${LIBSODIUM_TAG}
docker push docker.io/jesseswirldslabs/web3-run-noble-libsodium:${LIBSODIUM_TAG}

make sure that the builder.toml references the correct tag
[//]: # (tip: use --targets flag OR [[targets]] in builder.toml to specify the desired platform)
pack builder create docker.io/jesseswirldslabs/web3-builder-libsodium:${LIBSODIUM_TAG} \
--config web3/buildpack/builder.toml \
--publish

builder.set("web3-builder-libsodium:latest") // include docker.io/jesseswirldslabs if you want it to pull from registry

// buildpacks =
// listOf(
// "urn:cnb:builder:paketo-buildpacks/java",
// "paketobuildpacks/health-checker",
// "paketo-buildpacks/native-image",
// )
