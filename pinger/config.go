// SPDX-License-Identifier: Apache-2.0

package main

import (
	"flag"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

type config struct {
	network    string
	mirrorRest string // only used when network=other

	operatorID  string
	operatorKey string

	toAccountID   string
	amountTinybar int64
	interval      time.Duration

	maxRetries  int
	baseBackoff time.Duration

	mirrorNodeClientMaxRetries  int
	mirrorNodeClientBaseBackoff time.Duration
	mirrorNodeClientTimeout     time.Duration
}

func loadConfig() (config, error) {
	var cfg config

	flag.StringVar(&cfg.network, "network", envOr("HIERO_MIRROR_PINGER_NETWORK", "testnet"), "network: testnet|previewnet|mainnet|other")
	flag.StringVar(&cfg.mirrorRest, "mirror-rest", envOr("HIERO_MIRROR_PINGER_REST", ""), "mirror node REST base URL (required for other), e.g. http://mirror-rest:5551")

	flag.StringVar(&cfg.operatorID, "operator-id", envOr("HIERO_MIRROR_PINGER_OPERATOR_ID", "0.0.2"), "operator account id, e.g. 0.0.1234")
	flag.StringVar(&cfg.operatorKey, "operator-key", envOr("HIERO_MIRROR_PINGER_OPERATOR_KEY", "302e020100300506032b65700422042091132178e72057a1d7528025956fe39b0b847f200ab59b2fdd367017f3087137"), "operator private key string")

	flag.StringVar(&cfg.toAccountID, "to", envOr("HIERO_MIRROR_PINGER_TO_ACCOUNT_ID", "0.0.98"), "destination account id, e.g. 0.0.2345")

	amountStr := envOr("HIERO_MIRROR_PINGER_AMOUNT_TINYBAR", "10000")
	flag.Func("amount-tinybar", "amount in tinybar (int64)", func(s string) error {
		v, err := strconv.ParseInt(s, 10, 64)
		if err != nil {
			return err
		}
		cfg.amountTinybar = v
		return nil
	})
	_ = flag.CommandLine.Set("amount-tinybar", amountStr)

	intervalStr := envOr("HIERO_MIRROR_PINGER_INTERVAL", "1s")
	flag.DurationVar(&cfg.interval, "interval", toDuration(intervalStr), "interval between transfers (e.g. 1s, 500ms, 1m)")

	retriesStr := envOr("HIERO_MIRROR_PINGER_MAX_RETRIES", "10")
	flag.Func("max-retries", "max retries per tick", func(s string) error {
		v, err := strconv.Atoi(s)
		if err != nil {
			return err
		}
		cfg.maxRetries = v
		return nil
	})
	_ = flag.CommandLine.Set("max-retries", retriesStr)

	backoffStr := envOr("HIERO_MIRROR_PINGER_BASE_BACKOFF", "2s")
	flag.DurationVar(&cfg.baseBackoff, "base-backoff", toDuration(backoffStr), "base backoff for retries (e.g. 1s, 500ms)")

	mirrorNodeClientMaxRetriesStr := envOr("HIERO_MIRROR_PINGER_MIRROR_NODE_CLIENT_MAX_RETRIES", "10")
	flag.Func("mirror-node-client-max-retries", "max retries for mirror node client requests", func(s string) error {
		v, err := strconv.Atoi(s)
		if err != nil {
			return err
		}
		cfg.mirrorNodeClientMaxRetries = v
		return nil
	})
	_ = flag.CommandLine.Set("mirror-node-client-max-retries", mirrorNodeClientMaxRetriesStr)

	mirrorNodeClientBaseBackoffStr := envOr("HIERO_MIRROR_PINGER_MIRROR_NODE_CLIENT_BASE_BACKOFF", "500ms")
	flag.DurationVar(
		&cfg.mirrorNodeClientBaseBackoff,
		"mirror-node-client-base-backoff",
		toDuration(mirrorNodeClientBaseBackoffStr),
		"base backoff for mirror node client retries (e.g. 500ms, 1s)")

	mirrorNodeClientTimeoutStr := envOr("HIERO_MIRROR_PINGER_MIRROR_NODE_CLIENT_TIMEOUT", "10s")
	flag.DurationVar(
		&cfg.mirrorNodeClientTimeout,
		"mirror-node-client-retry-timeout",
		toDuration(mirrorNodeClientTimeoutStr),
		"HTTP timeout for mirror node client requests (e.g. 2s, 10s)")

	flag.Parse()

	// validate
	cfg.network = strings.TrimSpace(cfg.network)

	if cfg.operatorID == "" {
		return cfg, fmt.Errorf("missing operator id (set -operator-id or HIERO_MIRROR_PINGER_OPERATOR_ID)")
	}
	if cfg.operatorKey == "" {
		return cfg, fmt.Errorf("missing operator key (set -operator-key or HIERO_MIRROR_PINGER_OPERATOR_KEY)")
	}
	if cfg.toAccountID == "" {
		return cfg, fmt.Errorf("missing destination account id (set -to or HIERO_MIRROR_PINGER_TO_ACCOUNT_ID)")
	}
	if cfg.amountTinybar == 0 {
		return cfg, fmt.Errorf("amount must be non-zero")
	}
	if cfg.interval <= 0 {
		return cfg, fmt.Errorf("interval must be > 0")
	}
	if cfg.maxRetries < 0 {
		cfg.maxRetries = 0
	}
	if cfg.baseBackoff <= 0 {
		cfg.baseBackoff = 1 * time.Second
	}
	if cfg.mirrorNodeClientMaxRetries < 0 {
		cfg.mirrorNodeClientMaxRetries = 0
	}
	if cfg.mirrorNodeClientBaseBackoff <= 0 {
		cfg.mirrorNodeClientBaseBackoff = 500 * time.Millisecond
	}
	if cfg.mirrorNodeClientTimeout <= 0 {
		cfg.mirrorNodeClientTimeout = 10 * time.Second
	}

	if cfg.network == "other" && strings.TrimSpace(cfg.mirrorRest) == "" {
		return cfg, fmt.Errorf("HIERO_MIRROR_PINGER_NETWORK=other requires HIERO_MIRROR_PINGER_REST")
	}

	return cfg, nil
}

func envOr(key, def string) string {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		return v
	}
	return def
}

func toDuration(s string) time.Duration {
	d, err := time.ParseDuration(strings.TrimSpace(s))
	if err != nil {
		return 1 * time.Second
	}
	return d
}