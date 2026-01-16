// SPDX-License-Identifier: Apache-2.0

package domain

const tableNameAddressBookServiceEndpoint = "address_book_service_endpoint"

type AddressBookServiceEndpoint struct {
	ConsensusTimestamp int64  `gorm:"primaryKey"`
	IpAddressV4        string `gorm:"primaryKey"`
	NodeId             int64  `gorm:"primaryKey"`
	Port               int32  `gorm:"primaryKey"`
}

func (AddressBookServiceEndpoint) TableName() string {
	return tableNameAddressBookServiceEndpoint
}
