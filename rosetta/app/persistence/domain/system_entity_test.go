// SPDX-License-Identifier: Apache-2.0

package domain

import (
	"fmt"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/config"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestGetAddressBook(t *testing.T) {
	testSpecs := []struct {
		commonConfig config.CommonConfig
		expected101  EntityId
		expected102  EntityId
	}{
		{
			commonConfig: config.CommonConfig{},
			expected101:  MustDecodeEntityId(101),
			expected102:  MustDecodeEntityId(102),
		},
		{
			commonConfig: config.CommonConfig{Shard: 1, Realm: 2},
			expected101:  MustDecodeEntityId(18014948265295973),
			expected102:  MustDecodeEntityId(18014948265295974),
		},
	}

	for _, tt := range testSpecs {
		name := fmt.Sprintf("shard=%d,realm=%d", tt.commonConfig.Shard, tt.commonConfig.Realm)
		t.Run(name, func(t *testing.T) {
			subject := NewSystemEntity(tt.commonConfig)
			assert.Equal(t, tt.expected101, subject.GetAddressBook101())
			assert.Equal(t, tt.expected102, subject.GetAddressBook102())
		})
	}
}

func TestGetAddressBookPanics(t *testing.T) {
	subject := NewSystemEntity(config.CommonConfig{Realm: 65536, Shard: 1024})
	assert.Panics(t, func() {
		subject.GetAddressBook101()
	})
	assert.Panics(t, func() {
		subject.GetAddressBook102()
	})
}

func TestGetFeeCollectionAccount(t *testing.T) {
	testSpecs := []struct {
		commonConfig config.CommonConfig
		expected     EntityId
	}{
		{
			commonConfig: config.CommonConfig{},
			expected:     MustDecodeEntityId(802),
		},
		{
			commonConfig: config.CommonConfig{Shard: 1, Realm: 2},
			expected:     MustDecodeEntityId(18014948265296674),
		},
	}

	for _, tt := range testSpecs {
		t.Run(tt.expected.String(), func(t *testing.T) {
			subject := NewSystemEntity(tt.commonConfig)
			assert.Equal(t, tt.expected, subject.GetFeeCollectionAccount())
		})
	}
}

func TestGetFeeCollectionAccountPanics(t *testing.T) {
	subject := NewSystemEntity(config.CommonConfig{Realm: 65536, Shard: 1024})
	assert.Panics(t, func() {
		subject.GetFeeCollectionAccount()
	})
}

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
