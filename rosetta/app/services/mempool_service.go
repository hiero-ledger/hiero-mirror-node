// SPDX-License-Identifier: Apache-2.0

package services

import (
	"context"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/errors"

	"github.com/coinbase/rosetta-sdk-go/server"
	"github.com/coinbase/rosetta-sdk-go/types"
)

// mempoolAPIService implements the server.MempoolAPIServicer
type mempoolAPIService struct{}

// NewMempoolAPIService creates a new instance of a mempoolAPIService
func NewMempoolAPIService() server.MempoolAPIServicer {
	return &mempoolAPIService{}
}

// Mempool implements the /mempool endpoint
func (m *mempoolAPIService) Mempool(
	_ context.Context,
	_ *types.NetworkRequest,
) (*types.MempoolResponse, *types.Error) {
	return nil, errors.ErrNotImplemented
}

// MempoolTransaction implements the /mempool/transaction endpoint
func (m *mempoolAPIService) MempoolTransaction(
	_ context.Context,
	_ *types.MempoolTransactionRequest,
) (*types.MempoolTransactionResponse, *types.Error) {
	return nil, errors.ErrNotImplemented
}
