FROM --platform=$BUILDPLATFORM golang:1.27.4-trixie@sha256:b54b2639de8c1bfe538ec5ffc45f29b1932235ac5f6d63c059853646677112d9 AS build
ARG TARGETARCH
ARG TARGETOS
ARG VERSION=development
WORKDIR /app
COPY go.* ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH go build -ldflags="-w -s -X main.Version=${VERSION}" -o rosetta

FROM debian:trixie-202611104-slim@sha256:a72b4433de1cf7d6bae0c4cee74d0547f58601a643b3375f070c51600d63b6b2
ENV DEBIAN_FRONTEND=noninteractive
EXPOSE 5700
HEALTHCHECK --interval=10s --retries=5 --start-period=30s --timeout=2s CMD wget -q -O- http://localhost:5700/health/liveness
WORKDIR /app
RUN apt-get update && apt-get upgrade -y && apt-get install -y wget && rm -rf /var/lib/apt/lists/* # install OS updates
USER 1000:1000
COPY --from=build /app/rosetta .

CMD ["./rosetta"]
