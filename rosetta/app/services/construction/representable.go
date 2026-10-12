// SPDX-License-Identifier: Apache-2.0

package construction

import (
	"encoding/hex"
	"reflect"

	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/tools"
	"github.com/hiero-ledger/hiero-sdk-go/v2/proto/services"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	log "github.com/sirupsen/logrus"
	"google.golang.org/protobuf/proto"
)

const (
	metadataAlias               = "alias"
	metadataDeclineReward       = "decline_reward"
	metadataReceiverSigRequired = "receiver_sig_required"
	metadataStakedAccountID     = "staked_account_id"
	metadataStakedNodeID        = "staked_node_id"
)

func applyAccountCreateSemantics(metadata map[string]any, tx hiero.AccountCreateTransaction) {
	if len(tx.GetAlias()) > 0 {
		metadata[metadataAlias] = encodeHex(tx.GetAlias())
	}
	if pointerFieldSet(tx, "stakedAccountID") {
		metadata[metadataStakedAccountID] = tx.GetStakedAccountID().String()
	}
	if pointerFieldSet(tx, "stakedNodeID") {
		metadata[metadataStakedNodeID] = tx.GetStakedNodeID()
	} else if nodeID, ok := stakedNodeIDFromSignedBody(tx); ok {
		// The SDK drops a staked node of 0 when it loads a protobuf body.
		metadata[metadataStakedNodeID] = nodeID
	}
	if tx.GetDeclineStakingReward() {
		metadata[metadataDeclineReward] = true
	}
	if tx.GetReceiverSignatureRequired() {
		metadata[metadataReceiverSigRequired] = true
	}
}

func stakedNodeIDFromSignedBody(tx hiero.AccountCreateTransaction) (int64, bool) {
	if tx.Transaction == nil || !tx.IsFrozen() {
		return 0, false
	}

	body := &services.TransactionBody{}
	if err := proto.Unmarshal(tx.GetSignedTransactionBodyBytes(0), body); err != nil {
		log.Errorf("Failed to read the signed account create body: %s", err)
		return 0, false
	}

	staked, ok := body.GetCryptoCreateAccount().GetStakedId().(*services.CryptoCreateTransactionBody_StakedNodeId)
	if !ok {
		return 0, false
	}

	return staked.StakedNodeId, true
}

func pointerFieldSet(value any, name string) bool {
	field := reflect.ValueOf(value).FieldByName(name)
	if !field.IsValid() || field.Kind() != reflect.Pointer {
		log.Errorf("Account create field %s is not readable", name)
		return false
	}

	return !field.IsNil()
}

func encodeHex(value []byte) string {
	return tools.SafeAddHexPrefix(hex.EncodeToString(value))
}
