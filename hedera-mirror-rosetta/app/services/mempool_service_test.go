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
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/errors"
	"testing"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/stretchr/testify/assert"
)

func TestNewMempoolAPIService(t *testing.T) {
	mempoolService := NewMempoolAPIService()

	assert.IsType(t, &mempoolAPIService{}, mempoolService)
}

func TestMempool(t *testing.T) {
	// when:
	res, e := NewMempoolAPIService().Mempool(defaultContext, &rTypes.NetworkRequest{})

	// then:
	assert.Equal(t, errors.ErrNotImplemented, e)
	assert.Nil(t, res)
}

func TestMempoolTransaction(t *testing.T) {
	// when:
	res, e := NewMempoolAPIService().MempoolTransaction(defaultContext, &rTypes.MempoolTransactionRequest{})

	// then:
	assert.Equal(t, errors.ErrNotImplemented, e)
	assert.Nil(t, res)
}
