// SPDX-License-Identifier: Apache-2.0

package cmd

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/spf13/cobra"
	"golang.org/x/text/language"
	"golang.org/x/text/message"

	"mirrornode-bootstrap/internal/config"
)

var (
	cfgFile         string
	cfg             *config.Config
	logger          *slog.Logger
	logFile         *os.File
	discrepancyFile *os.File
	numPrinter      = message.NewPrinter(language.English) // for comma-formatted numbers
)

var rootCmd = &cobra.Command{
	Use:   "mirrornode-bootstrap",
	Short: "Mirror Node Database Bootstrap Tool",
	Long:  "High-performance tool for bootstrapping Mirror Node databases with parallel imports.",
	CompletionOptions: cobra.CompletionOptions{
		DisableDefaultCmd: true,
	},
	PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
		var err error
		cfg, err = config.LoadFromEnvFile(cfgFile)
		if err != nil {
			return fmt.Errorf("failed to load config: %w", err)
		}
		return nil
	},
}

func init() {
	rootCmd.PersistentFlags().StringVarP(&cfgFile, "config", "c", "", "Path to bootstrap.env config file")
	rootCmd.AddCommand(newInitCmd())
	rootCmd.AddCommand(newImportCmd())
	rootCmd.AddCommand(newStatusCmd())
	rootCmd.AddCommand(newWatchCmd())
}

func Execute() {
	// Initial logger to stderr (will be updated with file output after data dir is known)
	logger = slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelInfo}))

	// Setup signal handling for graceful shutdown
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		sig := <-sigChan
		logger.Warn("Received signal, shutting down...", "signal", sig)
		cancel() // Cancel context - all workers will stop
		// Give workers a moment to clean up, then exit
		time.Sleep(2 * time.Second)
		logger.Error("Forced shutdown")
		os.Exit(1)
	}()

	if err := rootCmd.ExecuteContext(ctx); err != nil {
		os.Exit(1)
	}
}
