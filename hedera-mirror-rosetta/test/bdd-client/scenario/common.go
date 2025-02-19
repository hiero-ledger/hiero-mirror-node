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

package scenario

import (
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/cucumber/godog"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/test/bdd-client/client"
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
