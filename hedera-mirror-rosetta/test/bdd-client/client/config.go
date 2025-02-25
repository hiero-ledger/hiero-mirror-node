// SPDX-License-Identifier: Apache-2.0

package client

import (
	"fmt"
	"time"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	"github.com/pkg/errors"
)

type Operator struct {
	Id         hiero.AccountID
	PrivateKey hiero.PrivateKey
}

func (o Operator) String() string {
	return fmt.Sprintf("{Id: %s PrivateKey: ***}", o.Id)
}

type retry struct {
	BackOff time.Duration
	Max     int
}

func (r retry) Run(work func() (bool, *types.Error, error), forceRetryOnRosettaError bool) (*types.Error, error) {
	var done bool
	var rosettaErr *types.Error
	var err error
	i := 1
	for {
		done, rosettaErr, err = work()
		if rosettaErr != nil {
			if !forceRetryOnRosettaError && !rosettaErr.Retriable {
				break
			}
		} else if err != nil {
			break
		} else if done {
			break
		}

		if i > r.Max {
			err = errors.Errorf("Exceeded %d retries", r.Max)
			break
		}

		i += 1
		time.Sleep(r.BackOff)
	}

	return rosettaErr, err
}

type Server struct {
	DataRetry   retry
	HttpTimeout time.Duration
	Network     map[string]hiero.AccountID
	OfflineUrl  string
	OnlineUrl   string
	SubmitRetry retry
}
