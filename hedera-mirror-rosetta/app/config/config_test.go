// SPDX-License-Identifier: Apache-2.0

package config

import (
	"io/ioutil"
	"os"
	"path/filepath"
	"reflect"
	"testing"
	"time"

	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	"github.com/stretchr/testify/assert"
	"gopkg.in/yaml.v2"
)

const (
	invalidYaml                   = "this is invalid"
	invalidYamlIncorrectAccountId = `
hedera:
  mirror:
    rosetta:
      nodes:
        "192.168.0.1:50211": 0.3`
	testConfigFilename = "application.yml"
	yml1               = `
hedera:
  mirror:
    rosetta:
      db:
        port: 5431
        username: foobar
      nodeRefreshInterval: 30m`
	yml2 = `
hedera:
  mirror:
    rosetta:
      db:
        host: 192.168.120.51
        port: 12000
      network: TESTNET`
	serviceEndpoint = "192.168.0.1:50211"
)

var expectedNodeRefreshInterval = 30 * time.Minute

func TestLoadDefaultConfig(t *testing.T) {
	config, err := LoadConfig()

	assert.NoError(t, err)
	assert.Equal(t, getDefaultConfig(), config)
}

func TestLoadDefaultConfigInvalidYamlString(t *testing.T) {
	original := defaultConfig
	defaultConfig = "foobar"

	config, err := LoadConfig()

	defaultConfig = original
	assert.Error(t, err)
	assert.Nil(t, config)
}

func TestLoadCustomConfig(t *testing.T) {
	tests := []struct {
		name    string
		fromCwd bool
	}{
		{name: "from current directory", fromCwd: true},
		{name: "from env var"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			tempDir, filePath := createYamlConfigFile(yml1, t)
			defer os.RemoveAll(tempDir)

			if tt.fromCwd {
				os.Chdir(tempDir)
			} else {
				em := envManager{}
				em.SetEnv(apiConfigEnvKey, filePath)
				t.Cleanup(em.Cleanup)
			}

			config, err := LoadConfig()

			assert.NoError(t, err)
			assert.NotNil(t, config)
			assert.True(t, config.Online)
			assert.Equal(t, uint16(5431), config.Db.Port)
			assert.Equal(t, "foobar", config.Db.Username)
			assert.Equal(t, expectedNodeRefreshInterval, config.NodeRefreshInterval)
		})
	}
}

func TestLoadCustomConfigFromCwdAndEnvVar(t *testing.T) {
	// given
	tempDir1, _ := createYamlConfigFile(yml1, t)
	defer os.RemoveAll(tempDir1)
	os.Chdir(tempDir1)

	tempDir2, filePath2 := createYamlConfigFile(yml2, t)
	defer os.RemoveAll(tempDir2)

	em := envManager{}
	em.SetEnv(apiConfigEnvKey, filePath2)
	t.Cleanup(em.Cleanup)

	// when
	config, err := LoadConfig()

	// then
	expected := getDefaultConfig()
	expected.Db.Host = "192.168.120.51"
	expected.Db.Port = 12000
	expected.Db.Username = "foobar"
	expected.Network = "testnet"
	expected.NodeRefreshInterval = expectedNodeRefreshInterval
	assert.NoError(t, err)
	assert.Equal(t, expected, config)
}

func TestLoadCustomConfigFromEnvVar(t *testing.T) {
	// given
	dbHost := "192.168.100.200"
	em := envManager{}
	em.SetEnv("HEDERA_MIRROR_ROSETTA_DB_HOST", dbHost)
	t.Cleanup(em.Cleanup)

	// when
	config, err := LoadConfig()

	// then
	expected := getDefaultConfig()
	expected.Db.Host = dbHost
	assert.NoError(t, err)
	assert.Equal(t, expected, config)
}

func TestLoadCustomConfigInvalidYaml(t *testing.T) {
	tests := []struct {
		name    string
		content string
		fromCwd bool
	}{
		{name: "invalid yaml", content: invalidYaml},
		{name: "invalid yaml from cwd", content: invalidYaml, fromCwd: true},
		{name: "incorrect account id", content: invalidYamlIncorrectAccountId},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			tempDir, filePath := createYamlConfigFile(tt.content, t)
			defer os.RemoveAll(tempDir)

			if tt.fromCwd {
				os.Chdir(tempDir)
			}

			em := envManager{}
			em.SetEnv(apiConfigEnvKey, filePath)
			t.Cleanup(em.Cleanup)

			config, err := LoadConfig()

			assert.Error(t, err)
			assert.Nil(t, config)
		})
	}
}

func TestLoadCustomConfigByEnvVarFileNotFound(t *testing.T) {
	// given
	em := envManager{}
	em.SetEnv(apiConfigEnvKey, "/foo/bar/not_found.yml")
	t.Cleanup(em.Cleanup)

	// when
	config, err := LoadConfig()

	// then
	assert.Error(t, err)
	assert.Nil(t, config)
}

func TestLoadNodeMapFromEnv(t *testing.T) {
	tests := []struct {
		value    string
		expected NodeMap
	}{
		{
			value:    "192.168.0.1:50211:0.0.3",
			expected: NodeMap{"192.168.0.1:50211": hiero.AccountID{Account: 3}},
		},
		{
			value: "192.168.0.1:50211:0.0.3,192.168.15.8:50211:0.0.4",
			expected: NodeMap{
				"192.168.0.1:50211":  hiero.AccountID{Account: 3},
				"192.168.15.8:50211": hiero.AccountID{Account: 4},
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.value, func(t *testing.T) {
			em := envManager{}
			em.SetEnv("HEDERA_MIRROR_ROSETTA_NODES", tt.value)
			t.Cleanup(em.Cleanup)

			// when
			config, err := LoadConfig()

			// then
			assert.Nil(t, err)
			assert.Equal(t, tt.expected, config.Nodes)
		})
	}
}

func TestLoadNodeMapFromEnvError(t *testing.T) {
	values := []string{"192.168.0.1:0.0.3", "192.168.0.1:50211:0.3", "192.168.0.1"}
	for _, value := range values {
		t.Run(value, func(t *testing.T) {
			em := envManager{}
			em.SetEnv("HEDERA_MIRROR_ROSETTA_NODES", value)
			t.Cleanup(em.Cleanup)

			// when
			config, err := LoadConfig()

			// then
			assert.Error(t, err)
			assert.Nil(t, config)
		})
	}
}

func TestNodeMapDecodeHookFunc(t *testing.T) {
	nodeMapTye := reflect.TypeOf(NodeMap{})
	tests := []struct {
		name        string
		from        reflect.Type
		data        interface{}
		expected    NodeMap
		expectError bool
	}{
		{
			name:     "valid data",
			from:     reflect.TypeOf(map[string]interface{}{}),
			data:     map[string]interface{}{serviceEndpoint: "0.0.3"},
			expected: NodeMap{serviceEndpoint: hiero.AccountID{Account: 3}},
		},
		{
			name:        "invalid data type",
			from:        reflect.TypeOf(map[int]string{}),
			data:        map[int]interface{}{1: "0.0.3"},
			expectError: true,
		},
		{
			name:        "invalid node account id",
			from:        reflect.TypeOf(map[string]interface{}{}),
			data:        map[string]interface{}{serviceEndpoint: "3"},
			expectError: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			actual, err := nodeMapDecodeHookFunc(tt.from, nodeMapTye, tt.data)

			if tt.expectError {
				assert.Error(t, err)
				assert.Nil(t, actual)
			} else {
				assert.NoError(t, err)
				assert.Equal(t, tt.expected, actual)
			}
		})
	}
}

func createYamlConfigFile(content string, t *testing.T) (string, string) {
	tempDir, err := ioutil.TempDir("", "rosetta")
	if err != nil {
		assert.Fail(t, "Unable to create temp dir", err)
	}

	customConfig := filepath.Join(tempDir, testConfigFilename)

	if err = ioutil.WriteFile(customConfig, []byte(content), 0644); err != nil {
		assert.Fail(t, "Unable to create custom config", err)
	}

	return tempDir, customConfig
}

type envManager struct {
	keys []string
}

func (e *envManager) SetEnv(key, value string) {
	os.Setenv(key, value)
	e.keys = append(e.keys, key)
}

func (e *envManager) Cleanup() {
	for _, key := range e.keys {
		os.Unsetenv(key)
	}
}

func getDefaultConfig() *Config {
	config := fullConfig{}
	yaml.Unmarshal([]byte(defaultConfig), &config)
	return &config.Hedera.Mirror.Rosetta
}
