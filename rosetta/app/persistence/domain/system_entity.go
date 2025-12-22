// SPDX-License-Identifier: Apache-2.0

package domain

import (
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/config"
	log "github.com/sirupsen/logrus"
)

const (
	addressBook101FileNum       = 101
	addressBook102FileNum       = 102
	feeCollectionAccountNum     = 802
	stakingRewardAccountNum     = 800
	treasuryAccountNum          = 2
)

type SystemEntity interface {
	GetAddressBook101() EntityId
	GetAddressBook102() EntityId
	GetFeeCollectionAccount() EntityId
	GetStakingRewardAccount() EntityId
	GetTreasuryAccount() EntityId
}

func NewSystemEntity(commonConfig config.CommonConfig) SystemEntity {
	return &systemEntity{commonConfig}
}

type systemEntity struct {
	commonConfig config.CommonConfig
}

func (s *systemEntity) GetAddressBook101() EntityId {
	entityId, err := EntityIdOf(s.commonConfig.Shard, s.commonConfig.Realm, addressBook101FileNum)
	if err != nil {
		log.Panicf("Failed to get address book 101 file id: %v", err)
	}
	return entityId
}

func (s *systemEntity) GetAddressBook102() EntityId {
	entityId, err := EntityIdOf(s.commonConfig.Shard, s.commonConfig.Realm, addressBook102FileNum)
	if err != nil {
		log.Panicf("Failed to get address book 102 file id: %v", err)
	}
	return entityId
}

func (s *systemEntity) GetFeeCollectionAccount() EntityId {
	entityId, err := EntityIdOf(s.commonConfig.Shard, s.commonConfig.Realm, feeCollectionAccountNum)
	if err != nil {
		log.Panicf("Failed to get fee collection account: %v", err)
	}
	return entityId
}

func (s *systemEntity) GetStakingRewardAccount() EntityId {
	entityId, err := EntityIdOf(s.commonConfig.Shard, s.commonConfig.Realm, stakingRewardAccountNum)
	if err != nil {
		log.Panicf("Failed to get staking reward account: %v", err)
	}
	return entityId
}

func (s *systemEntity) GetTreasuryAccount() EntityId {
	entityId, err := EntityIdOf(s.commonConfig.Shard, s.commonConfig.Realm, treasuryAccountNum)
	if err != nil {
		log.Panicf("Failed to get treasury account: %v", err)
	}
	return entityId
}
