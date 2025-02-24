// SPDX-License-Identifier: Apache-2.0

package types

import (
	"testing"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/stretchr/testify/assert"
)

func exampleBlock() *Block {
	return &Block{
		Index:               2,
		Hash:                "somehash",
		ConsensusStartNanos: 10000000,
		ConsensusEndNanos:   12300000,
		ParentIndex:         1,
		ParentHash:          "someparenthash",
		Transactions: []*Transaction{
			{
				Hash:       "somehash",
				Operations: OperationSlice{},
			},
		},
	}
}

func expectedBlock() *types.Block {
	return &types.Block{
		BlockIdentifier: &types.BlockIdentifier{
			Index: 2,
			Hash:  "0xsomehash",
		},
		ParentBlockIdentifier: &types.BlockIdentifier{
			Index: 1,
			Hash:  "0xsomeparenthash",
		},
		Timestamp: int64(10),
		Transactions: []*types.Transaction{
			{
				TransactionIdentifier: &types.TransactionIdentifier{Hash: "somehash"},
				Operations:            []*types.Operation{},
				Metadata:              map[string]interface{}{},
			},
		},
	}
}

func TestToRosettaBlock(t *testing.T) {
	// when:
	rosettaBlockResult := exampleBlock().ToRosetta()

	// then:
	assert.Equal(t, expectedBlock(), rosettaBlockResult)
}

func TestGetTimestampMillis(t *testing.T) {
	// given:
	exampleBlock := exampleBlock()

	// when:
	resultMillis := exampleBlock.GetTimestampMillis()

	// then:
	assert.Equal(t, int64(10), resultMillis)
}
