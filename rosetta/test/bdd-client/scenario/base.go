// SPDX-License-Identifier: Apache-2.0

package scenario

import (
	"context"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	log "github.com/sirupsen/logrus"
)

type baseFeature struct {
	transactionHash string
}

func (b *baseFeature) cleanup() {
	b.transactionHash = ""
}

func (b *baseFeature) findTransaction(ctx context.Context, operationType string) (*types.Transaction, error) {
	transaction, err := testClient.FindTransaction(ctx, b.transactionHash)
	if err != nil {
		log.Infof("Failed to find %s transaction with hash %s", operationType, b.transactionHash)
	}
	return transaction, err
}

func (b *baseFeature) submit(
	ctx context.Context,
	memo string,
	operations []*types.Operation,
	signers map[string]hiero.PrivateKey,
) (err error) {
	operationType := operations[0].Type
	b.transactionHash, err = testClient.Submit(ctx, memo, operations, signers)
	if err != nil {
		log.Errorf("Failed to submit %s transaction: %s", operationType, err)
	} else {
		log.Infof("Submitted %s transaction %s successfully", operationType, b.transactionHash)
	}
	return err
}
