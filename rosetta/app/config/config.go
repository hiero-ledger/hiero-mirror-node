// SPDX-License-Identifier: Apache-2.0

package config

import (
	"bytes"
	_ "embed"
	"fmt"
	"os"
	"reflect"
	"strings"

	"github.com/go-viper/mapstructure/v2"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
	"github.com/spf13/viper"
)

//go:embed application.yml
var defaultConfig string

const (
	apiConfigEnvKey = "HIERO_MIRROR_ROSETTA_API_CONFIG"
	configName      = "application"
	configTypeYaml  = "yml"
	envKeyDelimiter = "_"
	keyDelimiter    = "::"
	nodesEnvKey     = "HIERO_MIRROR_ROSETTA_NODES"
)

type Mirror struct {
	Common  CommonConfig
	Rosetta Config
}

type fullConfig struct {
	Hiero struct {
		Mirror Mirror
	}
}

// LoadConfig loads configuration from yaml files and env variables
func LoadConfig() (*Mirror, error) {
	nodeMap, err := loadNodeMapFromEnv()
	if err != nil {
		return nil, err
	}

	// NodeMap's key has '.', set viper key delimiter to avoid parsing it as a nested key
	v := viper.NewWithOptions(viper.KeyDelimiter(keyDelimiter))
	v.SetConfigType(configTypeYaml)

	// read the default
	if err := v.ReadConfig(bytes.NewBuffer([]byte(defaultConfig))); err != nil {
		return nil, err
	}

	// load configuration file from current directory
	v.SetConfigName(configName)
	v.AddConfigPath(".")
	if err := mergeExternalConfigFile(v); err != nil {
		return nil, err
	}

	if envConfigFile, ok := os.LookupEnv(apiConfigEnvKey); ok {
		v.SetConfigFile(envConfigFile)
		if err := mergeExternalConfigFile(v); err != nil {
			return nil, err
		}
	}

	// enable parsing env variables after the configuration files are loaded so viper knows all configuration keys
	// and can override the config accordingly
	v.AutomaticEnv()
	v.SetEnvKeyReplacer(strings.NewReplacer(keyDelimiter, envKeyDelimiter))

	var config fullConfig
	compositeDecodeHookFunc := mapstructure.ComposeDecodeHookFunc(
		mapstructure.StringToTimeDurationHookFunc(),
		nodeMapDecodeHookFunc,
	)
	if err := v.Unmarshal(&config, viper.DecodeHook(compositeDecodeHookFunc)); err != nil {
		return nil, err
	}

	mirrorConfig := &config.Hiero.Mirror
	mirrorConfig.Rosetta.Network = strings.ToLower(mirrorConfig.Rosetta.Network)
	if len(nodeMap) != 0 {
		mirrorConfig.Rosetta.Nodes = nodeMap
	}

	var password = mirrorConfig.Rosetta.Db.Password
	mirrorConfig.Rosetta.Db.Password = "***" // Don't print password
	log.Infof("Using configuration: %+v", &config)
	mirrorConfig.Rosetta.Db.Password = password

	return mirrorConfig, nil
}

func loadNodeMapFromEnv() (NodeMap, error) {
	nodeValue := os.Getenv(nodesEnvKey)
	os.Unsetenv(nodesEnvKey)
	if nodeValue == "" {
		return nil, nil
	}

	nodeInfoMap := make(map[string]interface{})
	for _, node := range strings.Split(nodeValue, ",") {
		parts := strings.Split(strings.TrimSpace(node), ":")
		if len(parts) != 3 {
			return nil, errors.Errorf("Invalid node string %s", node)
		}

		nodeInfoMap[fmt.Sprintf("%s:%s", parts[0], parts[1])] = parts[2]
	}

	nodeMap, err := nodeMapDecodeHookFunc(reflect.TypeOf(nodeInfoMap), reflect.TypeOf(NodeMap{}), nodeInfoMap)
	return nodeMap.(NodeMap), err
}

func mergeExternalConfigFile(v *viper.Viper) error {
	if err := v.MergeInConfig(); err != nil {
		if _, ok := err.(viper.ConfigFileNotFoundError); !ok {
			return err
		}

		log.Info("External configuration file not found")
		return nil
	}

	log.Infof("Loaded external config file: %s", v.ConfigFileUsed())
	return nil
}

func nodeMapDecodeHookFunc(from, to reflect.Type, data interface{}) (interface{}, error) {
	if to != reflect.TypeOf(NodeMap{}) {
		return data, nil
	}

	input, ok := data.(map[string]interface{})
	if !ok {
		return nil, errors.Errorf("Invalid data type for NodeMap")
	}

	var zeroNodeMap NodeMap
	nodeMap := make(NodeMap)
	for key, nodeAccountId := range input {
		nodeAccountIdStr, ok := nodeAccountId.(string)
		if !ok {
			return zeroNodeMap, errors.Errorf("Invalid data type for node account ID")
		}

		accountId, err := hiero.AccountIDFromString(nodeAccountIdStr)
		if err != nil {
			return zeroNodeMap, err
		}

		nodeMap[key] = accountId
	}

	return nodeMap, nil
}
