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
	"context"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/tools"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/cucumber/godog"
	types2 "github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	log "github.com/sirupsen/logrus"
)

type cryptoFeature struct {
	*baseFeature
	aliasAccountId types2.AccountId
	newAccountId   *hiero.AccountID // new account created during test
	newAccountKey  *hiero.PrivateKey
}

func (c *cryptoFeature) createCryptoAccount(ctx context.Context) error {
	if err := c.generateKey(); err != nil {
		return err
	}

	operations := []*types.Operation{
		{
			OperationIdentifier: &types.OperationIdentifier{Index: 0},
			Account:             getRosettaAccountIdentifier(testClient.GetOperator(0).Id),
			Amount: &types.Amount{
				// fund 10 hbar so the account can pay the transaction to delete itself
				Value:    "-1000000000",
				Currency: currencyHbar,
			},
			Type:     operationTypeCryptoCreateAccount,
			Metadata: map[string]interface{}{"key": c.newAccountKey.PublicKey().String()},
		},
	}

	return c.submit(ctx, "", operations, nil)
}

func (c *cryptoFeature) verifyCryptoCreateTransaction(ctx context.Context) error {
	transaction, err := c.findTransaction(ctx, operationTypeCryptoCreateAccount)
	if err != nil {
		return err
	}

	if err = assertTransactionAll(
		transaction,
		assertTransactionOpSuccess,
		assertTransactionMemo(""),
		assertTransactionOpCount(1, gte),
		assertTransactionOpTypesContains(operationTypeFee),
		assertTransactionMetadataAndType("entity_id", ""),
	); err != nil {
		return err
	}

	accountIdStr := transaction.Metadata["entity_id"].(string)
	accountId, err := hiero.AccountIDFromString(accountIdStr)
	if err != nil {
		log.Errorf("Invalid account id: %s", accountIdStr)
		return err
	}
	c.newAccountId = &accountId
	log.Infof("Successfully retrieved new account %s from transaction", accountIdStr)

	return nil
}

func (c *cryptoFeature) createCryptoAccountByAlias(ctx context.Context) error {
	if err := c.generateKey(); err != nil {
		return err
	}

	log.Infof("Transfer some hbar to new alias %s", c.aliasAccountId)
	operations := []*types.Operation{
		{
			OperationIdentifier: &types.OperationIdentifier{Index: 0},
			Account:             getRosettaAccountIdentifier(testClient.GetOperator(0).Id),
			Amount: &types.Amount{
				// fund 10 hbar so the account can pay the transaction to delete itself
				Value:    "-1000000000",
				Currency: currencyHbar,
			},
			Type: operationTypeCryptoTransfer,
		},
		{
			OperationIdentifier: &types.OperationIdentifier{Index: 1},
			Account:             c.aliasAccountId.ToRosetta(),
			Amount: &types.Amount{
				Value:    "1000000000",
				Currency: currencyHbar,
			},
			Type: operationTypeCryptoTransfer,
		},
	}
	return c.submit(ctx, "", operations, nil)
}

func (c *cryptoFeature) verifyCryptoTransferToAliasTransaction(ctx context.Context) error {
	transaction, err := c.findTransaction(ctx, operationTypeCryptoTransfer)
	if err != nil {
		return err
	}

	if err = assertTransactionAll(
		transaction,
		assertTransactionOpSuccess,
		assertTransactionOpCount(2, gte),
		assertTransactionOpTypesContains(operationTypeCryptoTransfer, operationTypeFee),
	); err != nil {
		return err
	}

	resp, err := testClient.GetAccountBalance(ctx, c.aliasAccountId.ToRosetta())
	if err != nil {
		return err
	}
	accountIdStr := resp.Metadata["account_id"].(string)
	accountId, err := hiero.AccountIDFromString(accountIdStr)
	if err != nil {
		log.Errorf("Invalid account id: %s", accountIdStr)
		return err
	}
	c.newAccountId = &accountId
	log.Infof("Successfully retrieved new account %s from account balance endpoint", accountIdStr)

	return nil
}

func (c *cryptoFeature) transferFromAlias(ctx context.Context) error {
	operations := []*types.Operation{
		{
			OperationIdentifier: &types.OperationIdentifier{Index: 0},
			Account:             c.aliasAccountId.ToRosetta(),
			Amount:              &types.Amount{Value: "-1", Currency: currencyHbar},
			Type:                operationTypeCryptoTransfer,
		},
		{
			OperationIdentifier: &types.OperationIdentifier{Index: 1},
			Account:             getRosettaAccountIdentifier(testClient.GetOperator(0).Id),
			Amount:              &types.Amount{Value: "1", Currency: currencyHbar},
			Type:                operationTypeCryptoTransfer,
		},
	}

	return c.submit(ctx, "", operations, map[string]hiero.PrivateKey{
		c.aliasAccountId.String(): *c.newAccountKey,
	})
}
func (c *cryptoFeature) verifyCryptoTransferFromAliasTransaction(ctx context.Context) error {
	transaction, err := c.findTransaction(ctx, operationTypeCryptoTransfer)
	if err != nil {
		return err
	}

	expectedAccountAmounts := []accountAmount{
		{
			Account: c.aliasAccountId.ToRosetta(),
			Amount:  &types.Amount{Value: "-1", Currency: currencyHbar},
		},
		{
			Account: getRosettaAccountIdentifier(testClient.GetOperator(0).Id),
			Amount:  &types.Amount{Value: "1", Currency: currencyHbar},
		},
	}

	return assertTransactionAll(
		transaction,
		assertTransactionOpSuccess,
		assertTransactionOpCount(2, gte),
		assertTransactionOpTypesContains(operationTypeCryptoTransfer, operationTypeFee),
		assertTransactionIncludesTransfers(expectedAccountAmounts),
	)
}

func (c *cryptoFeature) transferHbarToTreasury(ctx context.Context) error {
	operations := []*types.Operation{
		{
			OperationIdentifier: &types.OperationIdentifier{Index: 0},
			Account:             getRosettaAccountIdentifier(testClient.GetOperator(0).Id),
			Amount:              &types.Amount{Value: "-1", Currency: currencyHbar},
			Type:                operationTypeCryptoTransfer,
		},
		{
			OperationIdentifier: &types.OperationIdentifier{Index: 1},
			Account:             treasuryAccount,
			Amount:              &types.Amount{Value: "1", Currency: currencyHbar},
			Type:                operationTypeCryptoTransfer,
		},
	}

	return c.submit(ctx, "hbar transfer", operations, nil)
}

func (c *cryptoFeature) verifyCryptoTransferTransaction(ctx context.Context) error {
	transaction, err := c.findTransaction(ctx, operationTypeCryptoTransfer)
	if err != nil {
		return err
	}

	expectedAccountAmounts := []accountAmount{
		{
			Account: getRosettaAccountIdentifier(testClient.GetOperator(0).Id),
			Amount:  &types.Amount{Value: "-1", Currency: currencyHbar},
		},
		{
			Account: treasuryAccount,
			Amount:  &types.Amount{Value: "1", Currency: currencyHbar},
		},
	}

	return assertTransactionAll(
		transaction,
		assertTransactionOpSuccess,
		assertTransactionMemo("hbar transfer"),
		assertTransactionOpCount(2, gte),
		assertTransactionOpTypesContains(operationTypeCryptoTransfer, operationTypeFee),
		assertTransactionIncludesTransfers(expectedAccountAmounts),
	)
}

func (c *cryptoFeature) cleanup(ctx context.Context, s *godog.Scenario, err error) (context.Context, error) {
	log.Info("Cleaning up crypto feature")
	c.baseFeature.cleanup()

	if c.newAccountId != nil {
		testClient.DeleteAccount(*c.newAccountId, c.newAccountKey) // #nosec
	}

	c.newAccountId = nil
	c.newAccountKey = nil

	return ctx, err
}

func (c *cryptoFeature) generateKey() error {
	sk, err := hiero.PrivateKeyGenerateEd25519()
	if err != nil {
		log.Errorf("Failed to generate private key for new account: %v", err)
		return err
	}
	c.newAccountKey = &sk
	operatorAccountId := testClient.GetOperator(0).Id
	shard, err := tools.CastToInt64(operatorAccountId.Shard)
	if err != nil {
		return err
	}
	realm, err := tools.CastToInt64(operatorAccountId.Realm)
	if err != nil {
		return err
	}
	c.aliasAccountId, err = types2.NewAccountIdFromPublicKeyBytes(sk.PublicKey().BytesRaw(), shard, realm)
	if err != nil {
		return err
	}
	log.Debug("Generated private key for new account")
	return nil
}

func initializeCryptoScenario(ctx *godog.ScenarioContext) {
	crypto := &cryptoFeature{baseFeature: &baseFeature{}}

	ctx.After(crypto.cleanup)

	ctx.Step("I create a crypto account", crypto.createCryptoAccount)
	ctx.Step("the DATA API should show the CryptoCreate transaction", crypto.verifyCryptoCreateTransaction)

	ctx.Step("I transfer some hbar to a new alias", crypto.createCryptoAccountByAlias)
	ctx.Step("the DATA API should show the CryptoTransfer transaction and new account id",
		crypto.verifyCryptoTransferToAliasTransaction)

	ctx.Step("I transfer some hbar from the alias", crypto.transferFromAlias)
	ctx.Step("^the DATA API should show the CryptoTransfer transaction from the alias$",
		crypto.verifyCryptoTransferFromAliasTransaction)

	ctx.Step("I transfer some hbar to the treasury account", crypto.transferHbarToTreasury)
	ctx.Step("^the DATA API should show the CryptoTransfer transaction$", crypto.verifyCryptoTransferTransaction)
}
