// SPDX-License-Identifier: Apache-2.0

package persistence

import "github.com/jackc/pgtype"

func getInclusiveInt8Range(lower, upper int64) pgtype.Int8range {
	return pgtype.Int8range{
		Lower:     pgtype.Int8{Int: lower, Status: pgtype.Present},
		Upper:     pgtype.Int8{Int: upper, Status: pgtype.Present},
		LowerType: pgtype.Inclusive,
		UpperType: pgtype.Inclusive,
		Status:    pgtype.Present,
	}
}
