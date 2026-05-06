// SPDX-License-Identifier: Apache-2.0

package main

import (
	"context"
	"fmt"
	"log"
	"time"

	hiero "github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
)

func submitWithRetry(ctx context.Context, client *hiero.Client, cfg config) error {
	toID, err := hiero.AccountIDFromString(cfg.toAccountID)
	if err != nil {
		return fmt.Errorf("invalid destination id: %w", err)
	}

	var lastErr error
	attempts := cfg.maxRetries + 1

	for i := 1; i <= attempts; i++ {
		if err := ctx.Err(); err != nil {
			return err
		}

		start := time.Now()
		cryptoTransfer := hiero.NewTransferTransaction().
			AddHbarTransfer(client.GetOperatorAccountID(), hiero.HbarFromTinybar(-cfg.amountTinybar)).
			AddHbarTransfer(toID, hiero.HbarFromTinybar(cfg.amountTinybar))

		resp, err := cryptoTransfer.Execute(client)
		if err == nil {
			receipt, rerr := resp.GetReceipt(client)
			if rerr == nil {
				log.Printf("transfer success: status=%s txID=%s elapsed=%s",
					receipt.Status.String(), resp.TransactionID.String(), time.Since(start))
				return nil
			}
			err = rerr
		}

		lastErr = err
		log.Printf("attempt %d/%d failed: %v", i, attempts, err)

		if i < attempts {
			sleep := backoff(cfg.baseBackoff, i)
			timer := time.NewTimer(sleep)
			select {
			case <-ctx.Done():
				timer.Stop()
				return ctx.Err()
			case <-timer.C:
			}
		}
	}

	return fmt.Errorf("all attempts failed: %w", lastErr)
}

func backoff(base time.Duration, attempt int) time.Duration {
	d := base * time.Duration(1<<(attempt-1))
	return min(d, 30 * time.Second)
}