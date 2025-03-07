// SPDX-License-Identifier: Apache-2.0

package config

import (
	"fmt"
	"time"

	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
)

const EntityCacheKey = "entity"

type CommonConfig struct {
	Realm int64
	Shard int64
}

type Config struct {
	Cache               map[string]Cache
	Db                  Db
	Feature             Feature
	Http                Http
	Log                 Log
	Network             string
	NodeRefreshInterval time.Duration `yaml:"nodeRefreshInterval"`
	NodeVersion         string        `yaml:"nodeVersion"`
	Nodes               NodeMap
	Online              bool
	Port                uint16
	Response            Response
	ShutdownTimeout     time.Duration `yaml:"shutdownTimeout"`
}

type Cache struct {
	MaxSize int `yaml:"maxSize"`
}

type Db struct {
	Host             string
	Name             string
	Password         string
	Pool             Pool
	Port             uint16
	StatementTimeout int `yaml:"statementTimeout"`
	Username         string
}

func (db Db) GetDsn() string {
	return fmt.Sprintf(
		"host=%s port=%d user=%s dbname=%s password=%s sslmode=disable",
		db.Host,
		db.Port,
		db.Username,
		db.Name,
		db.Password,
	)
}

type Feature struct {
	SubNetworkIdentifier bool `yaml:"subNetworkIdentifier"`
}

type Http struct {
	IdleTimeout       time.Duration `yaml:"idleTimeout"`
	ReadTimeout       time.Duration `yaml:"readTimeout"`
	ReadHeaderTimeout time.Duration `yaml:"readHeaderTimeout"`
	WriteTimeout      time.Duration `yaml:"writeTimeout"`
}

type Log struct {
	Level string
}

type NodeMap map[string]hiero.AccountID

type Pool struct {
	MaxIdleConnections int `yaml:"maxIdleConnections"`
	MaxLifetime        int `yaml:"maxLifetime"`
	MaxOpenConnections int `yaml:"maxOpenConnections"`
}

type Response struct {
	MaxTransactionsInBlock int `yaml:"maxTransactionsInBlock"`
}
