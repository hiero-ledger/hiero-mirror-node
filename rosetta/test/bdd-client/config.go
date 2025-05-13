// SPDX-License-Identifier: Apache-2.0

package main

import (
	"bytes"
	"reflect"

	"github.com/go-viper/mapstructure/v2"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/test/bdd-client/client"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
	"github.com/spf13/viper"
)

const (
	configName     = "application"
	configPrefix   = "hiero::mirror::rosetta::test" // use '::' since it's the delimiter set for viper
	configTypeYaml = "yml"
	defaultConfig  = `
hiero:
  mirror:
    rosetta:
      test:
        log:
          level: debug
        operators:
          - id: 0.0.2
            privateKey: 302e020100300506032b65700422042091132178e72057a1d7528025956fe39b0b847f200ab59b2fdd367017f3087137
        server:
           dataRetry:
             backOff: 1s
             max: 60
           offlineUrl: http://localhost:5701
           onlineUrl: http://localhost:5700
           httpTimeout: 25s
           submitRetry:
             backOff: 200ms
             max: 100
`
	keyDelimiter = "::"
)

type config struct {
	Log       logConfig
	Operators []client.Operator
	Server    client.Server
}

type logConfig struct {
	Level string
}

func loadConfig() (*config, error) {
	// the nodes map's key has '.', set viper key delimiter to avoid parsing it as a nested key
	v := viper.NewWithOptions(viper.KeyDelimiter(keyDelimiter))
	v.SetConfigType(configTypeYaml)

	// read the default
	if err := v.ReadConfig(bytes.NewBuffer([]byte(defaultConfig))); err != nil {
		return nil, err
	}

	// load configuration file from the current director
	v.SetConfigName(configName)
	v.AddConfigPath(".")
	if err := v.MergeInConfig(); err != nil {
		log.Infof("Failed to load external configuration file not found: %v", err)
		return nil, err
	}
	log.Infof("Loaded external configuration file %s", v.ConfigFileUsed())

	config := &config{}
	if err := v.UnmarshalKey(configPrefix, config, addDecodeHooks); err != nil {
		log.Errorf("Failed to unmarshal config %v", err)
		return nil, err
	}

	return config, nil
}

func addDecodeHooks(c *mapstructure.DecoderConfig) {
	hooks := []mapstructure.DecodeHookFunc{accountIdDecodeHook, privateKeyDecodeHook}
	if c.DecodeHook != nil {
		hooks = append([]mapstructure.DecodeHookFunc{c.DecodeHook}, hooks...)
	}
	c.DecodeHook = mapstructure.ComposeDecodeHookFunc(hooks...)
}

func accountIdDecodeHook(from, to reflect.Type, data interface{}) (interface{}, error) {
	if to != reflect.TypeOf(hiero.AccountID{}) {
		return data, nil
	}

	if accountIdStr, ok := data.(string); ok {
		return hiero.AccountIDFromString(accountIdStr)
	} else {
		return nil, errors.Errorf("Invalid data type for hiero.AccountID")
	}
}

func privateKeyDecodeHook(from, to reflect.Type, data interface{}) (interface{}, error) {
	if to != reflect.TypeOf(hiero.PrivateKey{}) {
		return data, nil
	}

	if keyStr, ok := data.(string); ok {
		return hiero.PrivateKeyFromString(keyStr)
	} else {
		return nil, errors.Errorf("Invalid data type for hiero.PrivateKey")
	}
}
