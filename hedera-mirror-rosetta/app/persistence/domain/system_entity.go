// SPDX-License-Identifier: Apache-2.0

package domain

import (
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/config"
	log "github.com/sirupsen/logrus"
)

const (
	stakingRewardAccountNum = 800
	treasuryAccountNum      = 2
)

type SystemEntity interface {
	GetStakingRewardAccount() EntityId
	GetTreasuryAccount() EntityId
}

func NewSystemEntity(commonConfig config.CommonConfig) SystemEntity {
	return &systemEntity{commonConfig}
}

type systemEntity struct {
	commonConfig config.CommonConfig
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
