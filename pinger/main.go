// SPDX-License-Identifier: Apache-2.0

package main

import (
	"context"
	"errors"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"
)

var Version = "development"

func main() {
	log.Printf("pinger starting (version=%s)", Version)
	cfg, err := loadConfig()
	if err != nil {
		log.Fatalf("config error: %v", err)
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	// heartbeat for liveness exec probe (touches /tmp/alive)
	go func() {
		t := time.NewTicker(15 * time.Second)
		defer t.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-t.C:
				_ = os.WriteFile("/tmp/alive", []byte(time.Now().Format(time.RFC3339Nano)), 0644)
			}
		}
	}()

	client, err := newClient(cfg)
	if err != nil {
		log.Fatalf("client error: %v", err)
	}

	// Mark readiness for exec probe (creates /tmp/ready)
	if err := os.WriteFile("/tmp/ready", []byte("ok\n"), 0o644); err != nil {
		log.Fatalf("failed to create readiness file /tmp/ready: %v", err)
	}

	log.Printf("Starting transfer ticker: every %s, %d tinybar from %s -> %s on %s",
		cfg.interval, cfg.amountTinybar, cfg.operatorID, cfg.toAccountID, cfg.network)

	ticker := time.NewTicker(cfg.interval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			log.Printf("Shutting down")
			return
		case <-ticker.C:
			if err := submitWithRetry(ctx, client, cfg); err != nil {
				if errors.Is(err, context.Canceled) {
					return
				}
				log.Printf("transfer failed: %v", err)
			}
		}
	}
}