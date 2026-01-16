// SPDX-License-Identifier: Apache-2.0

package types

import (
	"time"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/tools"
)

// Block is domain level struct used to represent Block conceptual mapping in Hedera
type Block struct {
	ConsensusEndNanos   int64
	ConsensusStartNanos int64
	Hash                string
	Index               int64
	ParentHash          string
	ParentIndex         int64
	Transactions        []*Transaction
}

// ToRosetta returns Rosetta type Block from the current domain type Block
func (b *Block) ToRosetta() *types.Block {
	transactions := make([]*types.Transaction, len(b.Transactions))
	for i, t := range b.Transactions {
		transactions[i] = t.ToRosetta()
	}

	return &types.Block{
		BlockIdentifier: b.GetRosettaBlockIdentifier(),
		ParentBlockIdentifier: &types.BlockIdentifier{
			Index: b.ParentIndex,
			Hash:  tools.SafeAddHexPrefix(b.ParentHash),
		},
		Timestamp:    b.GetTimestampMillis(),
		Transactions: transactions,
	}
}

func (b *Block) GetRosettaBlockIdentifier() *types.BlockIdentifier {
	return &types.BlockIdentifier{
		Index: b.Index,
		Hash:  tools.SafeAddHexPrefix(b.Hash),
	}
}

// GetTimestampMillis returns the block timestamp in milliseconds
func (b *Block) GetTimestampMillis() int64 {
	return b.ConsensusStartNanos / int64(time.Millisecond)
}
