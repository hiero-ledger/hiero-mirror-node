// SPDX-License-Identifier: Apache-2.0

// Package config handles loading configuration from bootstrap.env and environment.
package config

import (
	"fmt"
	"github.com/spf13/viper"
	"os"
	"strings"
)

// Config holds all configuration values for the bootstrap process.
type Config struct {
	// PostgreSQL connection
	PGHost     string `mapstructure:"PGHOST"`
	PGPort     string `mapstructure:"PGPORT"`
	PGUser     string `mapstructure:"PGUSER"`
	PGPassword string `mapstructure:"PGPASSWORD"`
	PGDatabase string `mapstructure:"PGDATABASE"`

	// GCP settings
	IsGCPCloudSQL       bool `mapstructure:"IS_GCP_CLOUD_SQL"`
	CreateMirrorAPIUser bool `mapstructure:"CREATE_MIRROR_API_USER"`

	// User passwords
	GraphQLPassword  string `mapstructure:"GRAPHQL_PASSWORD"`
	GRPCPassword     string `mapstructure:"GRPC_PASSWORD"`
	ImporterPassword string `mapstructure:"IMPORTER_PASSWORD"`
	OwnerPassword    string `mapstructure:"OWNER_PASSWORD"`
	RESTPassword     string `mapstructure:"REST_PASSWORD"`
	RESTJavaPassword string `mapstructure:"REST_JAVA_PASSWORD"`
	RosettaPassword  string `mapstructure:"ROSETTA_PASSWORD"`
	Web3Password     string `mapstructure:"WEB3_PASSWORD"`

	// Import settings
	DecompressorThreads int `mapstructure:"DECOMPRESSOR_THREADS"`
	MaxJobs             int `mapstructure:"MAX_JOBS"`

	// Paths
	DataDir      string `mapstructure:"DATA_DIR"`
	ManifestFile string `mapstructure:"MANIFEST_FILE"`
	TrackingFile string `mapstructure:"TRACKING_FILE"`
	ProgressFile string `mapstructure:"PROGRESS_FILE"`
}

// DefaultConfig returns a Config with sensible defaults.
func DefaultConfig() *Config {
	return &Config{
		PGHost:              "localhost",
		PGPort:              "5432",
		PGUser:              "postgres",
		PGDatabase:          "mirror_node",
		IsGCPCloudSQL:       false,
		CreateMirrorAPIUser: true,
		DecompressorThreads: 4,
		MaxJobs:             8,
		TrackingFile:        "tracking.json",
		ProgressFile:        "progress.txt",
	}
}

// LoadFromEnvFile loads config from a bootstrap.env file, using viper.
func LoadFromEnvFile(path string) (*Config, error) {
	v := viper.New()

	// Set defaults
	v.SetDefault("PGHOST", "localhost")
	v.SetDefault("PGPORT", "5432")
	v.SetDefault("PGUSER", "postgres")
	v.SetDefault("PGDATABASE", "mirror_node")
	v.SetDefault("IS_GCP_CLOUD_SQL", false)
	v.SetDefault("CREATE_MIRROR_API_USER", true)
	v.SetDefault("DECOMPRESSOR_THREADS", 4)
	v.SetDefault("MAX_JOBS", 8)
	v.SetDefault("TRACKING_FILE", "tracking.json")
	v.SetDefault("PROGRESS_FILE", "progress.txt")
	// Bind to environment variables, then overlay with file if provided
	v.AutomaticEnv()
	if path != "" {
		info, err := os.Stat(path)
		if err != nil {
			return nil, fmt.Errorf("failed to open config file: %w", err)
		}
		if info.Size() > 0 {
			v.SetConfigFile(path)
			v.SetConfigType("env")
			if err := v.ReadInConfig(); err != nil {
				// Fallback to manual parsing if godotenv fails on malformed lines
				_ = err
				manualParse(v, path)
			}
		}
	}

	cfg := &Config{}

	// Fallback to manual population if Viper fails type conversion
	if err := v.Unmarshal(cfg); err != nil {
		if strings.Contains(err.Error(), "cannot parse value") {
			cfg = DefaultConfig()
			manualPopulate(v, cfg)
		} else {
			return nil, fmt.Errorf("unable to decode config into struct: %w", err)
		}
	}

	return cfg, nil
}

// manualParse reads an env file line by line, bypassing viper's strict godotenv parser.
func manualParse(v *viper.Viper, path string) {
	content, err := os.ReadFile(path)
	if err != nil {
		return
	}
	lines := strings.Split(string(content), "\n")
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		line = strings.TrimPrefix(line, "export ")
		parts := strings.SplitN(line, "=", 2)
		if len(parts) == 2 {
			key := strings.TrimSpace(parts[0])
			if key == "" {
				continue
			}
			val := strings.TrimSpace(parts[1])
			val = strings.Trim(val, `"'`)
			v.Set(key, val)
		}
	}
}

// manualPopulate fills the struct from viper, ignoring type errors for ints by using defaults.
func manualPopulate(v *viper.Viper, cfg *Config) {
	cfg.PGHost = v.GetString("PGHOST")
	cfg.PGPort = v.GetString("PGPORT")
	cfg.PGUser = v.GetString("PGUSER")
	cfg.PGPassword = v.GetString("PGPASSWORD")
	cfg.PGDatabase = v.GetString("PGDATABASE")
	cfg.IsGCPCloudSQL = v.GetBool("IS_GCP_CLOUD_SQL")
	cfg.CreateMirrorAPIUser = v.GetBool("CREATE_MIRROR_API_USER")
	cfg.GraphQLPassword = v.GetString("GRAPHQL_PASSWORD")
	cfg.GRPCPassword = v.GetString("GRPC_PASSWORD")
	cfg.ImporterPassword = v.GetString("IMPORTER_PASSWORD")
	cfg.OwnerPassword = v.GetString("OWNER_PASSWORD")
	cfg.RESTPassword = v.GetString("REST_PASSWORD")
	cfg.RESTJavaPassword = v.GetString("REST_JAVA_PASSWORD")
	cfg.RosettaPassword = v.GetString("ROSETTA_PASSWORD")
	cfg.Web3Password = v.GetString("WEB3_PASSWORD")
	cfg.DataDir = v.GetString("DATA_DIR")
	cfg.ManifestFile = v.GetString("MANIFEST_FILE")
	cfg.TrackingFile = v.GetString("TRACKING_FILE")
	cfg.ProgressFile = v.GetString("PROGRESS_FILE")

	if val := v.GetInt("DECOMPRESSOR_THREADS"); val != 0 {
		cfg.DecompressorThreads = val
	}
	if val := v.GetInt("MAX_JOBS"); val != 0 {
		cfg.MaxJobs = val
	}
}

// LoadFromEnv is maintained for backward compatibility with the CLI.
// Environment variable overlaying is mostly handled automatically by Viper now,
// but we keep this method signature so cmd/root.go still compiles cleanly.
func (c *Config) LoadFromEnv() {
	// Re-applying explicitly isn't needed with viper.AutomaticEnv(),
	// but we keep the method as a no-op to avoid breaking caller API,
	// or we can explicitly reload specific vars if we wanted to guarantee precedence.

	// For strict compatibility with old behavior where LoadFromEnv overrides regardless:
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
		var dummy int
		if _, err := fmt.Sscanf(v, "%d", &dummy); err == nil && dummy > 0 {
			c.MaxJobs = dummy
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
