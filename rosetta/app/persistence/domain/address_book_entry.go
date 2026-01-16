// SPDX-License-Identifier: Apache-2.0

package domain

const tableNameAddressBookEntry = "address_book_entry"

type AddressBookEntry struct {
	ConsensusTimestamp int64 `gorm:"primaryKey"`
	Memo               string
	PublicKey          string
	NodeId             int64 `gorm:"primaryKey"`
	NodeAccountId      EntityId
	NodeCertHash       []byte
	Description        string
	Stake              int64
}

func (AddressBookEntry) TableName() string {
	return tableNameAddressBookEntry
}
