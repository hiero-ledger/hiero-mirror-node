// SPDX-License-Identifier: Apache-2.0

package scenario

import (
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/cucumber/godog"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/test/bdd-client/client"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
)

const (
	operationStatusSuccess = "SUCCESS"

	operationTypeCryptoCreateAccount = "CRYPTOCREATEACCOUNT"
	operationTypeCryptoTransfer      = "CRYPTOTRANSFER"
	operationTypeFee                 = "FEE"
)

var (
	currencyHbar = &types.Currency{
		Symbol:   "HBAR",
		Decimals: 8,
		Metadata: map[string]interface{}{"issuer": "Hedera"},
	}
	testClient      client.Client
	treasuryAccount = getRosettaAccountIdentifier(hiero.AccountID{Account: 98})
)

func SetupTestClient(serverCfg client.Server, operators []client.Operator) {
	testClient = client.NewClient(serverCfg, operators)
}

func InitializeScenario(ctx *godog.ScenarioContext) {
	initializeCryptoScenario(ctx)
}

func getRosettaAccountIdentifier(accountId hiero.AccountID) *types.AccountIdentifier {
	return &types.AccountIdentifier{Address: accountId.String()}
}
