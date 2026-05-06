// SPDX-License-Identifier: Apache-2.0

package cmd

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/spf13/cobra"

	"mirrornode-bootstrap/internal/tracking"
)

func newStatusCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "status",
		Short: "Show import status",
		RunE: func(cmd *cobra.Command, args []string) error {
			// Get logs directory next to binary
			exePath, err := os.Executable()
			if err != nil {
				return fmt.Errorf("failed to get executable path: %w", err)
			}
			logsDir := filepath.Join(filepath.Dir(exePath), "bootstrap-logs")

			trackingPath := filepath.Join(logsDir, cfg.TrackingFile)
			tracker := tracking.NewTracker(trackingPath)
			if err := tracker.Open(); err != nil {
				return fmt.Errorf("failed to load tracking data: %w", err)
			}

			counts, err := tracker.CountByStatus()
			if err != nil {
				return err
			}

			fmt.Printf("Import Status (from %s):\n", trackingPath)
			fmt.Printf("  Imported:    %d\n", counts[tracking.StatusImported])
			fmt.Printf("  In Progress: %d\n", counts[tracking.StatusInProgress])
			fmt.Printf("  Failed:      %d\n", counts[tracking.StatusFailedToImport]+counts[tracking.StatusFailedValidation])
			fmt.Printf("  Not Started: %d\n", counts[tracking.StatusNotStarted])

			return nil
		},
	}

	return cmd
}
