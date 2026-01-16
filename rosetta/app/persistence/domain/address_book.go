// SPDX-License-Identifier: Apache-2.0

package domain

const tableNameAddressBook = "address_book"

type AddressBook struct {
	StartConsensusTimestamp int64 `gorm:"primaryKey"`
	EndConsensusTimestamp   *int64
	FileId                  EntityId
	NodeCount               int
	FileData                []byte
}

func (AddressBook) TableName() string {
	return tableNameAddressBook
}
