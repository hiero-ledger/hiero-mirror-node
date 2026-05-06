// SPDX-License-Identifier: Apache-2.0

package cmd

import (
	"fmt"
	"os/signal"
	"syscall"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/spf13/cobra"

	"mirrornode-bootstrap/internal/manifest"
	"mirrornode-bootstrap/internal/progress"
)

func newWatchCmd() *cobra.Command {
	var interval int
	var dataDir string
	var manifestFile string

	cmd := &cobra.Command{
		Use:   "watch",
		Short: "Watch live import progress",
		Long:  "Connects to the database and displays live COPY progress.\nRun this in a separate terminal while import is running.",
		RunE: func(cmd *cobra.Command, args []string) error {
			ctx := cmd.Context()

			// Use mirror_node credentials (same as import)
			cfg.PGUser = "mirror_node"
			cfg.PGDatabase = "mirror_node"
			cfg.PGPassword = cfg.OwnerPassword

			// Load manifest for expected row counts
			var mf *manifest.Manifest
			if manifestFile != "" {
				var err error
				mf, err = manifest.Load(manifestFile, dataDir)
				if err != nil {
					return fmt.Errorf("failed to load manifest: %w", err)
				}
				fmt.Printf("Loaded manifest with %d files\n", mf.Count())
			}

			// Connect to database
			conn, err := pgx.Connect(ctx, cfg.PgxConnectionString())
			if err != nil {
				return fmt.Errorf("failed to connect: %w", err)
			}
			defer conn.Close(ctx)

			// Create monitor
			monitor := progress.NewMonitor(conn, time.Duration(interval)*time.Second, "")
			startTime := time.Now()

			// Setup signal handling
			ctx, cancel := signal.NotifyContext(ctx, syscall.SIGINT, syscall.SIGTERM)
			defer cancel()

			fmt.Println("Watching import progress (Ctrl+C to stop)...")

			ticker := time.NewTicker(time.Duration(interval) * time.Second)
			defer ticker.Stop()

			for {
				select {
				case <-ctx.Done():
					fmt.Println("\nStopped.")
					return nil
				case <-ticker.C:
					progresses, err := monitor.FetchProgress(ctx)
					if err != nil {
						// Connection lost, try to display what we know
						fmt.Print("\033[2J\033[H")
						fmt.Println("Error fetching progress:", err)
						continue
					}
					// Enrich with manifest data if available
					if mf != nil {
						for i := range progresses {
							if entry, ok := mf.Get(progresses[i].Filename); ok {
								progresses[i].TotalRows = entry.RowCount
								if entry.RowCount > 0 {
									progresses[i].Percentage = float64(progresses[i].RowsProcessed) / float64(entry.RowCount) * 100
								}
							}
						}
					}
					printTerminalProgress(progresses, startTime)
				}
			}
		},
	}

	cmd.Flags().IntVarP(&interval, "interval", "i", 1, "Refresh interval in seconds")
	cmd.Flags().StringVarP(&dataDir, "data-dir", "d", "", "Directory containing data files")
	cmd.Flags().StringVarP(&manifestFile, "manifest", "m", "", "Path to manifest.csv file")

	return cmd
}

// printTerminalProgress outputs live progress to terminal
func printTerminalProgress(progresses []progress.FileProgress, startTime time.Time) {
	// Clear screen and move cursor to top (ANSI escape codes)
	fmt.Print("\033[2J\033[H")

	elapsed := time.Since(startTime).Round(time.Second)
	fmt.Printf("mirrornode-bootstrap - Import Progress (elapsed: %s)\n", elapsed)

	// Table: 45 filename + 30 rows/total + 8 pct + 12 rate + 5 spaces = 100 chars
	border := "════════════════════════════════════════════════════════════════════════════════════════════════════"
	divider := "────────────────────────────────────────────────────────────────────────────────────────────────────"

	fmt.Println(border)

	if len(progresses) == 0 {
		fmt.Println("No active imports...")
		fmt.Println(border)
		return
	}

	// Header
	fmt.Printf("%-45s %30s %8s %12s\n", "Filename", "Rows/Total", "%", "Rate")
	fmt.Println(divider)

	for _, p := range progresses {
		filename := p.Filename
		if len(filename) > 45 {
			filename = "..." + filename[len(filename)-42:]
		}

		pct := fmt.Sprintf("%.1f%%", p.Percentage)
		rate := numPrinter.Sprintf("%d/s", p.Rate)
		rowsTotal := numPrinter.Sprintf("%d / %d", p.RowsProcessed, p.TotalRows)

		fmt.Printf("%-45s %30s %8s %12s\n", filename, rowsTotal, pct, rate)
	}

	fmt.Println(border)
}
