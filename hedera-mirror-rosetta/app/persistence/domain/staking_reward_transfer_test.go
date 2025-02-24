// SPDX-License-Identifier: Apache-2.0

package domain

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestStakingRewardTransferTableName(t *testing.T) {
	assert.Equal(t, "staking_reward_transfer", StakingRewardTransfer{}.TableName())
}
