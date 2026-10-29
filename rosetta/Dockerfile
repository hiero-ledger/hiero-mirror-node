FROM --platform=$BUILDPLATFORM golang:1.27.3-trixie@sha256:fbb7ae40b62ff8afe31662df570d39b4f5052ac32b90dae8e60d7657a20a74f4 AS build
ARG TARGETARCH
ARG TARGETOS
ARG VERSION=development
WORKDIR /app
COPY go.* ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH go build -ldflags="-w -s -X main.Version=${VERSION}" -o rosetta

FROM debian:trixie-20261020-slim@sha256:62bd423aeee912639ca8ee681c2b762cb3e25137e0a92b20be455898a9f034ad
ENV DEBIAN_FRONTEND=noninteractive
EXPOSE 5700
HEALTHCHECK --interval=10s --retries=5 --start-period=30s --timeout=2s CMD wget -q -O- http://localhost:5700/health/liveness
WORKDIR /app
RUN apt-get update && apt-get upgrade -y && apt-get install -y wget && rm -rf /var/lib/apt/lists/* # install OS updates
USER 1000:1000
COPY --from=build /app/rosetta .

CMD ["./rosetta"]
