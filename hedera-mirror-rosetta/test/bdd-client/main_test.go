/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package main

import (
	"os"
	"strings"
	"testing"

	"github.com/cucumber/godog"
	"github.com/cucumber/godog/colors"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/test/bdd-client/scenario"
	log "github.com/sirupsen/logrus"
	"github.com/spf13/pflag"
)

var options = godog.Options{
	Output: colors.Colored(os.Stdout),
	Format: "pretty",
}

func init() {
	godog.BindCommandLineFlags("godog.", &options)
}

func configLogger(level string) {
	var err error
	var logLevel log.Level

	if logLevel, err = log.ParseLevel(strings.ToLower(level)); err != nil {
		// if invalid, default to info
		logLevel = log.InfoLevel
	}

	log.SetFormatter(&log.TextFormatter{
		DisableColors: true,
		FullTimestamp: true,
	})
	log.SetLevel(logLevel)
	log.SetOutput(os.Stdout)
}

func TestMain(m *testing.M) {
	pflag.Parse()
	options.Paths = pflag.Args()

	configLogger("info")

	// load config
	config, err := loadConfig()
	if err != nil {
		log.Fatal(err)
	}
	log.Infof("Config: %+v", config)
	configLogger(config.Log.Level)

	scenario.SetupTestClient(config.Server, config.Operators)
	status := godog.TestSuite{
		Name:                "hedera-mirror-rosetta-bdd-test",
		ScenarioInitializer: scenario.InitializeScenario,
		Options:             &options,
	}.Run()

	os.Exit(status)
}
