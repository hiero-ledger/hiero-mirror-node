// SPDX-License-Identifier: Apache-2.0

package cmd

import (
	"compress/gzip"
	"context"
	"fmt"
	"io"
	"os"
	"path/filepath"

	"github.com/spf13/cobra"
	"github.com/zeebo/blake3"

	"mirrornode-bootstrap/internal/buffers"
	"mirrornode-bootstrap/internal/database"
	"mirrornode-bootstrap/internal/manifest"
)

func newInitCmd() *cobra.Command {
	var dataDir string
	var schemaFile string

	cmd := &cobra.Command{
		Use:   "init",
		Short: "Initialize database with schema and roles",
		Long:  "Downloads init.sh from GitHub, creates the database, roles, permissions, then executes schema.sql.",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runInit(cmd.Context(), dataDir, schemaFile)
		},
	}

	cmd.Flags().StringVarP(&dataDir, "data-dir", "d", "", "Directory containing schema.sql")
	cmd.Flags().StringVarP(&schemaFile, "schema", "s", "", "Path to schema.sql file (overrides data-dir)")

	return cmd
}

func runInit(ctx context.Context, dataDir, schemaFile string) error {
	// Apply config defaults
	if cfg.DataDir != "" && dataDir == "" {
		dataDir = cfg.DataDir
	}

	// Get executable directory and create bootstrap-logs/ subdirectory
	exePath, err := os.Executable()
	if err != nil {
		return fmt.Errorf("failed to get executable path: %w", err)
	}
	logsDir := filepath.Join(filepath.Dir(exePath), "bootstrap-logs")
	if err := os.MkdirAll(logsDir, 0755); err != nil {
		return fmt.Errorf("failed to create logs directory: %w", err)
	}

	logger.Info("Starting database initialization",
		"host", cfg.PGHost,
		"port", cfg.PGPort,
		"admin_user", cfg.PGUser,
	)

	// Validate and decompress schema.sql.gz from manifest
	if dataDir != "" && schemaFile == "" {
		manifestPath := filepath.Join(dataDir, "manifest.csv")
		if _, err := os.Stat(manifestPath); err == nil {
			// Load manifest
			mf, err := manifest.Load(manifestPath, dataDir)
			if err != nil {
				return fmt.Errorf("failed to load manifest: %w", err)
			}

			// Validate schema.sql.gz if present in manifest
			if entry, ok := mf.Get("schema.sql.gz"); ok {
				schemaGzPath := filepath.Join(dataDir, "schema.sql.gz")

				// Validate file size
				info, err := os.Stat(schemaGzPath)
				if err != nil {
					return fmt.Errorf("schema.sql.gz not found: %w", err)
				}
				if info.Size() != entry.FileSize {
					return fmt.Errorf("schema.sql.gz size mismatch: expected %d, got %d", entry.FileSize, info.Size())
				}

				// Validate BLAKE3 hash
				file, err := os.Open(schemaGzPath)
				if err != nil {
					return fmt.Errorf("failed to open schema.sql.gz: %w", err)
				}
				defer file.Close()

				hasher := blake3.New()
				buf := buffers.GetDecompressBuffer()
				defer buffers.ReturnDecompressBuffer(buf)

				if _, err := io.CopyBuffer(hasher, file, buf); err != nil {
					return fmt.Errorf("failed to hash schema.sql.gz: %w", err)
				}

				actualHash := fmt.Sprintf("%x", hasher.Sum(nil))
				if actualHash != entry.Blake3Hash {
					return fmt.Errorf("schema.sql.gz hash mismatch: expected %s, got %s", entry.Blake3Hash, actualHash)
				}

				logger.Info("schema.sql.gz validated successfully", "size", entry.FileSize, "hash", actualHash[:16]+"...")

				// Decompress to schema.sql
				schemaPath := filepath.Join(dataDir, "schema.sql")
				gzFile, err := os.Open(schemaGzPath)
				if err != nil {
					return fmt.Errorf("failed to open schema.sql.gz for decompression: %w", err)
				}
				defer gzFile.Close()

				gzReader, err := gzip.NewReader(gzFile)
				if err != nil {
					return fmt.Errorf("failed to create gzip reader: %w", err)
				}
				defer gzReader.Close()

				outFile, err := os.Create(schemaPath)
				if err != nil {
					return fmt.Errorf("failed to create schema.sql: %w", err)
				}
				defer outFile.Close()

				if _, err := io.Copy(outFile, gzReader); err != nil {
					return fmt.Errorf("failed to decompress schema.sql.gz: %w", err)
				}

				logger.Info("schema.sql decompressed successfully", "path", schemaPath)
				schemaFile = schemaPath
			}
		}
	}

	initCfg := database.InitConfig{
		AdminHost:           cfg.PGHost,
		AdminPort:           cfg.PGPort,
		AdminUser:           cfg.PGUser,
		AdminPassword:       cfg.PGPassword,
		AdminDatabase:       cfg.PGDatabase,
		OwnerPassword:       cfg.OwnerPassword,
		GraphQLPassword:     cfg.GraphQLPassword,
		GRPCPassword:        cfg.GRPCPassword,
		ImporterPassword:    cfg.ImporterPassword,
		RESTPassword:        cfg.RESTPassword,
		RESTJavaPassword:    cfg.RESTJavaPassword,
		RosettaPassword:     cfg.RosettaPassword,
		Web3Password:        cfg.Web3Password,
		SchemaFile:          schemaFile,
		DataDir:             dataDir,
		LogsDir:             logsDir,
		IsGCPCloudSQL:       cfg.IsGCPCloudSQL,
		CreateMirrorAPIUser: cfg.CreateMirrorAPIUser,
	}

	if err := database.Initialize(ctx, initCfg); err != nil {
		logger.Error("Database initialization failed", "error", err)
		return err
	}

	logger.Info("Database initialization complete")
	return nil
}
