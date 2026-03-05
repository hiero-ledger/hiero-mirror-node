# syntax=docker/dockerfile:1.6

############################
# Builder (multi-arch)
############################
FROM --platform=$BUILDPLATFORM golang:1.26.0-alpine3.23 AS builder

WORKDIR /src

ARG TARGETOS
ARG TARGETARCH
ARG VERSION=development
ENV CGO_ENABLED=0

RUN apk add --no-cache ca-certificates

COPY go.mod go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod \
    go mod download

COPY . .

RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    GOOS=$TARGETOS GOARCH=$TARGETARCH \
    go build -trimpath -ldflags="-s -w -X main.Version=${VERSION}" -o /out/pinger .

RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    GOOS=$TARGETOS GOARCH=$TARGETARCH \
    go build -trimpath -ldflags="-s -w" -o /out/healthcheck ./cmd/healthcheck

############################
# Runtime
############################
FROM scratch

COPY --from=builder /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/
COPY --from=builder /out/pinger /pinger
COPY --from=builder /out/healthcheck /healthcheck

USER 1000:1000
ENTRYPOINT ["/pinger"]