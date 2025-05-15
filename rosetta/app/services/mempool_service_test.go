// SPDX-License-Identifier: Apache-2.0

package services

import (
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/errors"
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
