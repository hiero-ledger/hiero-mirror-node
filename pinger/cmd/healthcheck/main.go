// SPDX-License-Identifier: Apache-2.0

package main

import (
	"fmt"
	"os"
	"time"
)

func main() {
	mode := "ready"
	if len(os.Args) > 1 {
		mode = os.Args[1]
	}

	switch mode {
	case "ready":
		// Ready if init marker exists
		if _, err := os.Stat("/tmp/ready"); err != nil {
			fmt.Fprintln(os.Stderr, "not ready")
			os.Exit(1)
		}
		os.Exit(0)

	case "live":
		fi, err := os.Stat("/tmp/alive")
		if err != nil {
			// Missing/invalid heartbeat file => not alive
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}

		age := time.Since(fi.ModTime())
		if age > 2*time.Minute {
			fmt.Fprintln(os.Stderr, "heartbeat stale")
			os.Exit(1)
		}
		os.Exit(0)

	default:
		fmt.Fprintln(os.Stderr, "usage: healthcheck [ready|live]")
		os.Exit(2)
	}
}