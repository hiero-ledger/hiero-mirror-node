// SPDX-License-Identifier: Apache-2.0

package domain

import (
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/config"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestGetStakingRewardAccount(t *testing.T) {
	testSpecs := []struct {
		commonConfig config.CommonConfig
		expected     EntityId
	}{
		{
			commonConfig: config.CommonConfig{},
			expected:     MustDecodeEntityId(800),
		},
		{
			commonConfig: config.CommonConfig{Shard: 1, Realm: 2},
			expected:     MustDecodeEntityId(18014948265296672),
		},
	}

	for _, tt := range testSpecs {
		t.Run(tt.expected.String(), func(t *testing.T) {
			subject := NewSystemEntity(tt.commonConfig)
			assert.Equal(t, tt.expected, subject.GetStakingRewardAccount())
		})
	}
}

func TestGetStakingRewardAccountPanics(t *testing.T) {
	subject := NewSystemEntity(config.CommonConfig{Realm: 65536, Shard: 1024})
	assert.Panics(t, func() {
		subject.GetStakingRewardAccount()
	})
}

func TestGetTreasuryAccount(t *testing.T) {
	testSpecs := []struct {
		commonConfig config.CommonConfig
		expected     EntityId
	}{
		{
			commonConfig: config.CommonConfig{},
			expected:     MustDecodeEntityId(2),
		},
		{
			commonConfig: config.CommonConfig{Shard: 1, Realm: 2},
			expected:     MustDecodeEntityId(18014948265295874),
		},
	}

	for _, tt := range testSpecs {
		t.Run(tt.expected.String(), func(t *testing.T) {
			subject := NewSystemEntity(tt.commonConfig)
			assert.Equal(t, tt.expected, subject.GetTreasuryAccount())
		})
	}
}

func TestGetTreasuryAccountPanics(t *testing.T) {
	subject := NewSystemEntity(config.CommonConfig{Realm: 65536, Shard: 1024})
	assert.Panics(t, func() {
		subject.GetTreasuryAccount()
	})
}
