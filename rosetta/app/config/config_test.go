// SPDX-License-Identifier: Apache-2.0

package config

import (
	"os"
	"path/filepath"
	"reflect"
	"strconv"
	"testing"
	"time"

	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	"github.com/stretchr/testify/assert"
	"gopkg.in/yaml.v2"
)

const (
	invalidYaml                   = "this is invalid"
	invalidYamlIncorrectAccountId = `
hiero:
  mirror:
    rosetta:
      nodes:
        "192.168.0.1:50211": 0.3`
	testConfigFilename = "application.yml"
	yml1               = `
hiero:
  mirror:
    rosetta:
      db:
        port: 5431
        username: foobar
      nodeRefreshInterval: 30m`
	yml2 = `
hiero:
  mirror:
    common:
      realm: 1000
      shard: 1
    rosetta:
      db:
        host: 192.168.120.51
        port: 12000
      network: TESTNET`
	realmEnvKey     = "HIERO_MIRROR_COMMON_REALM"
	serviceEndpoint = "192.168.0.1:50211"
	shardEnvKey     = "HIERO_MIRROR_COMMON_SHARD"
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
				em := newEnvManager()
				em.SetEnv(apiConfigEnvKey, filePath)
				t.Cleanup(em.Cleanup)
			}

			config, err := LoadConfig()

			assert.NoError(t, err)
			assert.NotNil(t, config)
			assert.True(t, config.Rosetta.Online)
			assert.Equal(t, uint16(5431), config.Rosetta.Db.Port)
			assert.Equal(t, "foobar", config.Rosetta.Db.Username)
			assert.Equal(t, expectedNodeRefreshInterval, config.Rosetta.NodeRefreshInterval)
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

	em := newEnvManager()
	em.SetEnv(apiConfigEnvKey, filePath2)
	em.UnsetEnv(realmEnvKey)
	em.UnsetEnv(shardEnvKey)
	t.Cleanup(em.Cleanup)

	// when
	config, err := LoadConfig()

	// then
	expected := getDefaultConfig()
	expected.Common.Realm = 1000
	expected.Common.Shard = 1
	expected.Rosetta.Db.Host = "192.168.120.51"
	expected.Rosetta.Db.Port = 12000
	expected.Rosetta.Db.Username = "foobar"
	expected.Rosetta.Network = "testnet"
	expected.Rosetta.NodeRefreshInterval = expectedNodeRefreshInterval
	assert.NoError(t, err)
	assert.Equal(t, expected, config)
}

func TestLoadCustomConfigFromEnvVar(t *testing.T) {
	// given
	dbHost := "192.168.100.200"
	em := newEnvManager()
	em.SetEnv("HIERO_MIRROR_ROSETTA_DB_HOST", dbHost)
	t.Cleanup(em.Cleanup)

	// when
	config, err := LoadConfig()

	// then
	expected := getDefaultConfig()
	expected.Rosetta.Db.Host = dbHost
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

			em := newEnvManager()
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
	em := newEnvManager()
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
			em := newEnvManager()
			em.SetEnv("HIERO_MIRROR_ROSETTA_NODES", tt.value)
			t.Cleanup(em.Cleanup)

			// when
			config, err := LoadConfig()

			// then
			assert.Nil(t, err)
			assert.Equal(t, tt.expected, config.Rosetta.Nodes)
		})
	}
}

func TestLoadNodeMapFromEnvError(t *testing.T) {
	values := []string{"192.168.0.1:0.0.3", "192.168.0.1:50211:0.3", "192.168.0.1"}
	for _, value := range values {
		t.Run(value, func(t *testing.T) {
			em := newEnvManager()
			em.SetEnv("HIERO_MIRROR_ROSETTA_NODES", value)
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
	tempDir, err := os.MkdirTemp("", "rosetta")
	if err != nil {
		assert.Fail(t, "Unable to create temp dir", err)
	}

	customConfig := filepath.Join(tempDir, testConfigFilename)

	if err = os.WriteFile(customConfig, []byte(content), 0644); err != nil {
		assert.Fail(t, "Unable to create custom config", err)
	}

	return tempDir, customConfig
}

type envManager struct {
	added      []string
	overridden map[string]string
}

func (e *envManager) SetEnv(key, value string) {
	if oldValue, present := os.LookupEnv(key); present {
		e.overridden[key] = oldValue
	} else {
		e.added = append(e.added, key)
	}
	os.Setenv(key, value)
}

func (e *envManager) UnsetEnv(key string) {
	if value, present := os.LookupEnv(key); present {
		e.overridden[key] = value
	}
	os.Unsetenv(key)
}

func (e *envManager) Cleanup() {
	for _, key := range e.added {
		os.Unsetenv(key)
	}

	for key, value := range e.overridden {
		os.Setenv(key, value)
	}
}

func newEnvManager() envManager {
	return envManager{overridden: make(map[string]string)}
}

func getDefaultConfig() *Mirror {
	config := fullConfig{}
	yaml.Unmarshal([]byte(defaultConfig), &config)

	if value, present := os.LookupEnv(realmEnvKey); present {
		var err error
		config.Hiero.Mirror.Common.Realm, err = strconv.ParseInt(value, 10, 64)
		if err != nil {
			panic(err)
		}
	}

	if value, present := os.LookupEnv(shardEnvKey); present {
		var err error
		config.Hiero.Mirror.Common.Shard, err = strconv.ParseInt(value, 10, 64)
		if err != nil {
			panic(err)
		}
	}

	return &config.Hiero.Mirror
}
