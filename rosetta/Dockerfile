FROM --platform=$BUILDPLATFORM golang:1.27.0-trixie@sha256:df98008ecd2b0ecc9f0a94d1b07e3564a9c92b555369b33d9b5f60d0765b2db7 AS build
ARG TARGETARCH
ARG TARGETOS
ARG VERSION=development
WORKDIR /app
COPY go.* ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH go build -ldflags="-w -s -X main.Version=${VERSION}" -o rosetta

FROM debian:trixie-20260824-slim@sha256:d7e12182ce18b85b93007c1dedf31f2d29e01ccf3182cc4017c709b6259bc132
ENV DEBIAN_FRONTEND=noninteractive
EXPOSE 5700
HEALTHCHECK --interval=10s --retries=5 --start-period=30s --timeout=2s CMD wget -q -O- http://localhost:5700/health/liveness
WORKDIR /app
RUN apt-get update && apt-get upgrade -y && apt-get install -y wget && rm -rf /var/lib/apt/lists/* # install OS updates
USER 1000:1000
COPY --from=build /app/rosetta .

CMD ["./rosetta"]
