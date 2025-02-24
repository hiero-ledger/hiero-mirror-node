// SPDX-License-Identifier: Apache-2.0

package domain

const tableNameAccountBalance = "account_balance"

type AccountBalance struct {
	AccountId          EntityId `gorm:"primaryKey"`
	Balance            int64
	ConsensusTimestamp int64 `gorm:"primaryKey"`
}

func (AccountBalance) TableName() string {
	return tableNameAccountBalance
}
