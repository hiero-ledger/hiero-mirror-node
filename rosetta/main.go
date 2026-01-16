// SPDX-License-Identifier: Apache-2.0

package main

import (
	"context"
	"fmt"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/persistence/domain"
	"net/http"
	"os"
	"os/signal"
	"runtime"
	"strings"
	"syscall"

	rosettaAsserter "github.com/coinbase/rosetta-sdk-go/asserter"
	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/config"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/db"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/interfaces"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/middleware"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/persistence"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/services"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/services/construction"
	log "github.com/sirupsen/logrus"
)

const moduleName = "rosetta"

var Version = "development"

func configLogger(level string) {
	var err error
	var logLevel log.Level

	if logLevel, err = log.ParseLevel(strings.ToLower(level)); err != nil {
		// if invalid, default to info
		logLevel = log.InfoLevel
	}

	log.SetFormatter(&log.TextFormatter{ // Use logfmt for easy parsing by Loki
		CallerPrettyfier: func(frame *runtime.Frame) (function string, file string) {
			parts := strings.Split(frame.File, moduleName)
			relativeFilepath := parts[len(parts)-1]
			// remove function name, show file path relative to project root
			return "", fmt.Sprintf("%s:%d", relativeFilepath, frame.Line)
		},
		DisableColors: true,
		FullTimestamp: true,
	})
	log.SetLevel(logLevel)
	log.SetOutput(os.Stdout)
	log.SetReportCaller(logLevel >= log.DebugLevel)
}

// newBlockchainOnlineRouter creates a Mux http.Handler from a collection
// of server controllers, serving "online" mode.
// ref: https://www.rosetta-api.org/docs/node_deployment.html#online-mode-endpoints
func newBlockchainOnlineRouter(
	asserter *rosettaAsserter.Asserter,
	dbClient interfaces.DbClient,
	network *rTypes.NetworkIdentifier,
	mirrorConfig *config.Mirror,
	serverContext context.Context,
	version *rTypes.Version,
) (http.Handler, error) {
	systemEntity := domain.NewSystemEntity(mirrorConfig.Common)
	accountRepo := persistence.NewAccountRepository(dbClient, systemEntity.GetTreasuryAccount())
	addressBookEntryRepo := persistence.NewAddressBookEntryRepository(systemEntity.GetAddressBook101(), systemEntity.GetAddressBook102(), dbClient)
	blockRepo := persistence.NewBlockRepository(dbClient, systemEntity.GetTreasuryAccount())
	transactionRepo := persistence.NewTransactionRepository(dbClient, systemEntity.GetStakingRewardAccount())

	baseService := services.NewOnlineBaseService(blockRepo, transactionRepo)

	networkAPIService := services.NewNetworkAPIService(baseService, addressBookEntryRepo, network, version)
	networkAPIController := server.NewNetworkAPIController(networkAPIService, asserter)

	blockAPIService := services.NewBlockAPIService(
		accountRepo,
		baseService,
		mirrorConfig.Rosetta.Cache[config.EntityCacheKey],
		mirrorConfig.Rosetta.Response.MaxTransactionsInBlock,
		serverContext,
	)
	blockAPIController := server.NewBlockAPIController(blockAPIService, asserter)

	mempoolAPIService := services.NewMempoolAPIService()
	mempoolAPIController := server.NewMempoolAPIController(mempoolAPIService, asserter)

	constructionAPIService, err := services.NewConstructionAPIService(
		accountRepo,
		baseService,
		mirrorConfig,
		construction.NewTransactionConstructor(),
	)
	if err != nil {
		return nil, err
	}
	constructionAPIController := server.NewConstructionAPIController(constructionAPIService, asserter)

	accountAPIService := services.NewAccountAPIService(baseService, accountRepo, mirrorConfig.Common.Shard, mirrorConfig.Common.Realm)
	accountAPIController := server.NewAccountAPIController(accountAPIService, asserter)
	healthController, err := middleware.NewHealthController(&mirrorConfig.Rosetta)
	metricsController := middleware.NewMetricsController()
	if err != nil {
		return nil, err
	}

	return server.NewRouter(
		networkAPIController,
		blockAPIController,
		mempoolAPIController,
		constructionAPIController,
		accountAPIController,
		healthController,
		metricsController,
	), nil
}

// newBlockchainOfflineRouter creates a Mux http.Handler from a collection
// of server controllers, serving "offline" mode.
// ref: https://www.rosetta-api.org/docs/node_deployment.html#offline-mode-endpoints
func newBlockchainOfflineRouter(
	asserter *rosettaAsserter.Asserter,
	network *rTypes.NetworkIdentifier,
	mirrorConfig *config.Mirror,
	version *rTypes.Version,
) (http.Handler, error) {
	baseService := services.NewOfflineBaseService()

	constructionAPIService, err := services.NewConstructionAPIService(
		nil,
		baseService,
		mirrorConfig,
		construction.NewTransactionConstructor(),
	)
	if err != nil {
		return nil, err
	}
	constructionAPIController := server.NewConstructionAPIController(constructionAPIService, asserter)
	healthController, err := middleware.NewHealthController(&mirrorConfig.Rosetta)
	if err != nil {
		return nil, err
	}

	metricsController := middleware.NewMetricsController()
	networkAPIService := services.NewNetworkAPIService(baseService, nil, network, version)
	networkAPIController := server.NewNetworkAPIController(networkAPIService, asserter)

	return server.NewRouter(constructionAPIController, healthController, metricsController, networkAPIController), nil
}

func main() {
	configLogger("info")

	mirrorConfig, err := config.LoadConfig()
	if err != nil {
		log.Fatalf("Failed to load config: %s", err)
	}

	rosettaConfig := mirrorConfig.Rosetta
	log.Infof("%s version %s, rosetta api version %s", moduleName, Version, rTypes.RosettaAPIVersion)

	configLogger(rosettaConfig.Log.Level)

	network := &rTypes.NetworkIdentifier{
		Blockchain: types.Blockchain,
		Network:    strings.ToLower(rosettaConfig.Network),
	}

	if rosettaConfig.Feature.SubNetworkIdentifier {
		network.SubNetworkIdentifier = &rTypes.SubNetworkIdentifier{
			Network: fmt.Sprintf("shard %d realm %d", mirrorConfig.Common.Shard, mirrorConfig.Common.Realm),
		}
	}

	version := &rTypes.Version{
		RosettaVersion:    rTypes.RosettaAPIVersion,
		NodeVersion:       rosettaConfig.NodeVersion,
		MiddlewareVersion: &Version,
	}

	asserter, err := rosettaAsserter.NewServer(
		types.SupportedOperationTypes,
		true,
		[]*rTypes.NetworkIdentifier{network},
		nil,
		false,
		"",
	)
	if err != nil {
		log.Fatal(err)
	}

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	var router http.Handler
	if rosettaConfig.Online {
		dbClient := db.ConnectToDb(rosettaConfig.Db)

		router, err = newBlockchainOnlineRouter(asserter, dbClient, network, mirrorConfig, ctx, version)
		if err != nil {
			log.Fatal(err)
		}

		log.Info("Serving Rosetta API in ONLINE mode")
	} else {
		router, err = newBlockchainOfflineRouter(asserter, network, mirrorConfig, version)
		if err != nil {
			log.Fatal(err)
		}

		log.Info("Serving Rosetta API in OFFLINE mode")
	}

	metricsMiddleware := middleware.MetricsMiddleware(router)
	tracingMiddleware := middleware.TracingMiddleware(metricsMiddleware)
	corsMiddleware := server.CorsMiddleware(tracingMiddleware)
	httpServer := &http.Server{
		Addr:              fmt.Sprintf(":%d", rosettaConfig.Port),
		Handler:           corsMiddleware,
		IdleTimeout:       rosettaConfig.Http.IdleTimeout,
		ReadHeaderTimeout: rosettaConfig.Http.ReadHeaderTimeout,
		ReadTimeout:       rosettaConfig.Http.ReadTimeout,
		WriteTimeout:      rosettaConfig.Http.WriteTimeout,
	}

	go func() {
		log.Infof("Listening on port %d", rosettaConfig.Port)
		if err := httpServer.ListenAndServe(); err != nil {
			log.Errorf("Error http listen and serve: %v", err)
			stop()
		}
	}()

	<-ctx.Done()
	stop()

	shutdownCtx, cancel := context.WithTimeout(context.Background(), rosettaConfig.ShutdownTimeout)
	defer cancel()
	if err := httpServer.Shutdown(shutdownCtx); err == nil {
		log.Info("Server shutdown gracefully")
	} else {
		log.Errorf("Error shutdown server: %v", err)
	}
}
