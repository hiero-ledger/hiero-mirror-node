// SPDX-License-Identifier: Apache-2.0

package interfaces

import (
	"context"

	"gorm.io/gorm"
)

// DbClient Interface that all db clients must implement
type DbClient interface {

	// GetDb returns the gorm.DB instance
	GetDb() *gorm.DB

	// GetDbWithContext returns the gorm.DB instance with the context and the cancel function
	GetDbWithContext(ctx context.Context) (*gorm.DB, context.CancelFunc)
}
