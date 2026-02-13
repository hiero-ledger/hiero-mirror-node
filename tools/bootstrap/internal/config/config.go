// Package config handles loading configuration from bootstrap.env and environment.
package config

import (
	"bufio"
	"fmt"
	"os"
	"strconv"
	"strings"
)

// Config holds all configuration values for the bootstrap process.
type Config struct {
	// PostgreSQL connection
	PGHost     string
	PGPort     string
	PGUser     string
	PGPassword string
	PGDatabase string

	// GCP settings
	IsGCPCloudSQL     bool
	CreateMirrorAPIUser bool

	// User passwords
	GraphQLPassword   string
	GRPCPassword      string
	ImporterPassword  string
	OwnerPassword     string
	RESTPassword      string
	RESTJavaPassword  string
	RosettaPassword   string
	Web3Password      string

	// Import settings
	DecompressorThreads int
	MaxJobs             int // parallel imports

	// Paths
	DataDir       string
	ManifestFile  string
	TrackingFile  string
	ProgressFile  string
}

// DefaultConfig returns a Config with sensible defaults.
func DefaultConfig() *Config {
	return &Config{
		PGHost:              "localhost",
		PGPort:              "5432",
		PGUser:              "postgres",
		PGDatabase:          "mirror_node",
		IsGCPCloudSQL:       false, // Default false
		CreateMirrorAPIUser: true,  // Default true
		DecompressorThreads: 4,
		MaxJobs:             8,
		TrackingFile:        "tracking.json",
		ProgressFile:        "progress.txt",
	}
}

// LoadFromEnvFile loads config from a bootstrap.env file.
// The file should contain lines like: export VAR="value"
func LoadFromEnvFile(path string) (*Config, error) {
	cfg := DefaultConfig()

	file, err := os.Open(path)
	if err != nil {
		return nil, fmt.Errorf("failed to open config file: %w", err)
	}
	defer file.Close()

	envVars := make(map[string]string)
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())

		// Skip comments and empty lines
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}

		// Parse export VAR="value" or VAR="value"
		line = strings.TrimPrefix(line, "export ")
		parts := strings.SplitN(line, "=", 2)
		if len(parts) != 2 {
			continue
		}

		key := strings.TrimSpace(parts[0])
		value := strings.TrimSpace(parts[1])

		// Remove surrounding quotes
		value = strings.Trim(value, `"'`)

		envVars[key] = value
	}

	if err := scanner.Err(); err != nil {
		return nil, fmt.Errorf("error reading config file: %w", err)
	}

	// Map env vars to config
	if v, ok := envVars["PGHOST"]; ok {
		cfg.PGHost = v
	}
	if v, ok := envVars["PGPORT"]; ok {
		cfg.PGPort = v
	}
	if v, ok := envVars["PGUSER"]; ok {
		cfg.PGUser = v
	}
	if v, ok := envVars["PGPASSWORD"]; ok {
		cfg.PGPassword = v
	}
	if v, ok := envVars["PGDATABASE"]; ok {
		cfg.PGDatabase = v
	}
	if v, ok := envVars["IS_GCP_CLOUD_SQL"]; ok {
		cfg.IsGCPCloudSQL = strings.ToLower(v) == "true"
	}
	if v, ok := envVars["CREATE_MIRROR_API_USER"]; ok {
		cfg.CreateMirrorAPIUser = strings.ToLower(v) == "true"
	}
	if v, ok := envVars["GRAPHQL_PASSWORD"]; ok {
		cfg.GraphQLPassword = v
	}
	if v, ok := envVars["GRPC_PASSWORD"]; ok {
		cfg.GRPCPassword = v
	}
	if v, ok := envVars["IMPORTER_PASSWORD"]; ok {
		cfg.ImporterPassword = v
	}
	if v, ok := envVars["OWNER_PASSWORD"]; ok {
		cfg.OwnerPassword = v
	}
	if v, ok := envVars["REST_PASSWORD"]; ok {
		cfg.RESTPassword = v
	}
	if v, ok := envVars["REST_JAVA_PASSWORD"]; ok {
		cfg.RESTJavaPassword = v
	}
	if v, ok := envVars["ROSETTA_PASSWORD"]; ok {
		cfg.RosettaPassword = v
	}
	if v, ok := envVars["WEB3_PASSWORD"]; ok {
		cfg.Web3Password = v
	}
	if v, ok := envVars["DECOMPRESSOR_THREADS"]; ok {
		if i, err := strconv.Atoi(v); err == nil {
			cfg.DecompressorThreads = i
		}
	}
	if v, ok := envVars["MAX_JOBS"]; ok {
		if i, err := strconv.Atoi(v); err == nil {
			cfg.MaxJobs = i
		}
	}
	if v, ok := envVars["DATA_DIR"]; ok {
		cfg.DataDir = v
	}
	if v, ok := envVars["MANIFEST_FILE"]; ok {
		cfg.ManifestFile = v
	}
	if v, ok := envVars["TRACKING_FILE"]; ok {
		cfg.TrackingFile = v
	}
	if v, ok := envVars["PROGRESS_FILE"]; ok {
		cfg.ProgressFile = v
	}

	return cfg, nil
}

// LoadFromEnv loads config from current environment variables.
// Overlays on top of existing config.
func (c *Config) LoadFromEnv() {
	if v := os.Getenv("PGHOST"); v != "" {
		c.PGHost = v
	}
	if v := os.Getenv("PGPORT"); v != "" {
		c.PGPort = v
	}
	if v := os.Getenv("PGUSER"); v != "" {
		c.PGUser = v
	}
	if v := os.Getenv("PGPASSWORD"); v != "" {
		c.PGPassword = v
	}
	if v := os.Getenv("PGDATABASE"); v != "" {
		c.PGDatabase = v
	}
	if v := os.Getenv("DATA_DIR"); v != "" {
		c.DataDir = v
	}
	if v := os.Getenv("MANIFEST_FILE"); v != "" {
		c.ManifestFile = v
	}
	if v := os.Getenv("MAX_JOBS"); v != "" {
		if i, _ := strconv.Atoi(v); i > 0 {
			c.MaxJobs = i
		}
	}
}

// ConnectionString returns the PostgreSQL connection string.
func (c *Config) ConnectionString() string {
	return fmt.Sprintf("host=%s port=%s user=%s password=%s dbname=%s sslmode=disable",
		c.PGHost, c.PGPort, c.PGUser, c.PGPassword, c.PGDatabase)
}

// PgxConnectionString returns the connection string in pgx format.
func (c *Config) PgxConnectionString() string {
	return fmt.Sprintf("postgres://%s:%s@%s:%s/%s?sslmode=disable",
		c.PGUser, c.PGPassword, c.PGHost, c.PGPort, c.PGDatabase)
}
