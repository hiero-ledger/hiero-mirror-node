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

package services

import (
	"context"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/errors"

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
