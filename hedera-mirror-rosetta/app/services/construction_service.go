/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"github.com/hiero-ledger/hiero-sdk-go/v2/proto/services"
	"golang.org/x/exp/maps"
	"math/big"
	"strings"
	"time"

	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/config"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/services/construction"
	"github.com/hiero-ledger/hiero-mirror-node/hedera-mirror-rosetta/app/tools"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	log "github.com/sirupsen/logrus"
	"google.golang.org/protobuf/encoding/prototext"
)

const (
	maxValidDurationSeconds         = 180
	defaultValidDurationSeconds     = maxValidDurationSeconds
	maxValidDurationNanos           = maxValidDurationSeconds * 1_000_000_000
	metadataKeyAccountMap           = "account_map"
	metadataKeyNodeAccountId        = "node_account_id"
	metadataKeyValidDurationSeconds = "valid_duration"
	metadataKeyValidStartNanos      = "valid_start_nanos"
	metadataKeyValidUntilNanos      = "valid_until_nanos"
	optionKeyAccountAliases         = "account_aliases"
	optionKeyOperationType          = "operation_type"
)

// constructionAPIService implements the server.ConstructionAPIServicer interface.
type constructionAPIService struct {
	BaseService
	accountRepo        interfaces.AccountRepository
	hederaClient       *hiero.Client
	systemShard        int64
	systemRealm        int64
	transactionHandler construction.TransactionConstructor
}

// ConstructionCombine implements the /construction/combine endpoint.
func (c *constructionAPIService) ConstructionCombine(
	_ context.Context,
	request *rTypes.ConstructionCombineRequest,
) (*rTypes.ConstructionCombineResponse, *rTypes.Error) {
	if len(request.Signatures) == 0 {
		return nil, errors.ErrNoSignature
	}

	transaction, rErr := unmarshallTransactionFromHexString(request.UnsignedTransaction)
	if rErr != nil {
		return nil, rErr
	}

	frozenBodyBytes, rErr := getFrozenTransactionBodyBytes(transaction)
	if rErr != nil {
		return nil, rErr
	}

	for _, signature := range request.Signatures {
		if signature.SignatureType != rTypes.Ed25519 {
			return nil, errors.ErrInvalidSignatureType
		}

		pubKey, err := hiero.PublicKeyFromBytes(signature.PublicKey.Bytes)
		if err != nil {
			return nil, errors.ErrInvalidPublicKey
		}

		if !ed25519.Verify(pubKey.Bytes(), frozenBodyBytes, signature.Bytes) {
			return nil, errors.ErrInvalidSignatureVerification
		}

		_, _ = hiero.TransactionAddSignature(transaction, pubKey, signature.Bytes)
	}

	transactionBytes, err := hiero.TransactionToBytes(transaction)
	if err != nil {
		return nil, errors.ErrTransactionMarshallingFailed
	}

	return &rTypes.ConstructionCombineResponse{
		SignedTransaction: tools.SafeAddHexPrefix(hex.EncodeToString(transactionBytes)),
	}, nil
}

// ConstructionDerive implements the /construction/derive endpoint.
func (c *constructionAPIService) ConstructionDerive(
	_ context.Context,
	request *rTypes.ConstructionDeriveRequest,
) (*rTypes.ConstructionDeriveResponse, *rTypes.Error) {
	publicKey := request.PublicKey
	if publicKey.CurveType != rTypes.Edwards25519 {
		return nil, errors.ErrInvalidPublicKey
	}

	accountId, err := types.NewAccountIdFromPublicKeyBytes(request.PublicKey.Bytes, c.systemShard, c.systemRealm)
	if err != nil || accountId.GetCurveType() != rTypes.Edwards25519 {
		return nil, errors.ErrInvalidPublicKey
	}
	return &rTypes.ConstructionDeriveResponse{AccountIdentifier: accountId.ToRosetta()}, nil
}

// ConstructionHash implements the /construction/hash endpoint.
func (c *constructionAPIService) ConstructionHash(
	_ context.Context,
	request *rTypes.ConstructionHashRequest,
) (*rTypes.TransactionIdentifierResponse, *rTypes.Error) {
	signedTransaction, rErr := unmarshallTransactionFromHexString(request.SignedTransaction)
	if rErr != nil {
		return nil, rErr
	}

	hash, err := hiero.TransactionGetTransactionHash(signedTransaction)
	if err != nil {
		return nil, errors.ErrTransactionHashFailed
	}

	return &rTypes.TransactionIdentifierResponse{
		TransactionIdentifier: &rTypes.TransactionIdentifier{Hash: tools.SafeAddHexPrefix(hex.EncodeToString(hash[:]))},
	}, nil
}

// ConstructionMetadata implements the /construction/metadata endpoint.
func (c *constructionAPIService) ConstructionMetadata(
	ctx context.Context,
	request *rTypes.ConstructionMetadataRequest,
) (*rTypes.ConstructionMetadataResponse, *rTypes.Error) {
	options := request.Options
	if options == nil || options[optionKeyOperationType] == nil {
		return nil, errors.ErrInvalidOptions
	}

	operationType, ok := options[optionKeyOperationType].(string)
	if !ok {
		return nil, errors.ErrInvalidOptions
	}

	maxFee, rErr := c.transactionHandler.GetDefaultMaxTransactionFee(operationType)
	if rErr != nil {
		return nil, rErr
	}

	metadata := maps.Clone(request.Options)
	delete(metadata, optionKeyAccountAliases)

	if resolved, rErr := c.resolveAccountAliases(ctx, options); rErr != nil {
		return nil, rErr
	} else if resolved != "" {
		metadata[metadataKeyAccountMap] = resolved
	}

	// node account id
	nodeAccountId, rErr := c.getRandomNodeAccountId()
	if rErr != nil {
		return nil, rErr
	}
	metadata[metadataKeyNodeAccountId] = nodeAccountId.String()

	// pass the value as a string to avoid being deserialized into data types like float64
	validUntilNanos := time.Now().UnixNano() + maxValidDurationNanos
	metadata[metadataKeyValidUntilNanos] = fmt.Sprintf("%d", validUntilNanos)

	return &rTypes.ConstructionMetadataResponse{
		Metadata:     metadata,
		SuggestedFee: []*rTypes.Amount{maxFee.ToRosetta()},
	}, nil
}

// ConstructionParse implements the /construction/parse endpoint.
func (c *constructionAPIService) ConstructionParse(
	ctx context.Context,
	request *rTypes.ConstructionParseRequest,
) (*rTypes.ConstructionParseResponse, *rTypes.Error) {
	transaction, err := unmarshallTransactionFromHexString(request.Transaction)
	if err != nil {
		return nil, err
	}

	metadata := make(map[string]interface{})
	memo, _ := hiero.TransactionGetTransactionMemo(transaction)
	if memo != "" {
		metadata[types.MetadataKeyMemo] = memo
	}

	operations, accounts, err := c.transactionHandler.Parse(ctx, transaction)
	if err != nil {
		return nil, err
	}

	signers := make([]*rTypes.AccountIdentifier, 0, len(accounts))
	if request.Signed {
		for _, account := range accounts {
			signers = append(signers, account.ToRosetta())
		}
	}

	return &rTypes.ConstructionParseResponse{
		AccountIdentifierSigners: signers,
		Metadata:                 metadata,
		Operations:               operations.ToRosetta(),
	}, nil
}

// ConstructionPayloads implements the /construction/payloads endpoint.
func (c *constructionAPIService) ConstructionPayloads(
	ctx context.Context,
	request *rTypes.ConstructionPayloadsRequest,
) (*rTypes.ConstructionPayloadsResponse, *rTypes.Error) {
	nodeAccountId, rErr := c.getTransactionNodeAccountId(request.Metadata)
	if rErr != nil {
		return nil, rErr
	}

	validDurationSeconds, validStartNanos, rErr := c.getTransactionTimestampProperty(request.Metadata)
	if rErr != nil {
		return nil, rErr
	}

	operations, rErr := c.getOperationSlice(request.Operations)
	if rErr != nil {
		return nil, rErr
	}

	transaction, signers, rErr := c.transactionHandler.Construct(ctx, operations)
	if rErr != nil {
		return nil, rErr
	}

	// signers[0] is always the payer account id
	payer, rErr := c.getSdkPayerAccountId(signers[0], request.Metadata[metadataKeyAccountMap])
	if rErr != nil {
		return nil, rErr
	}

	if rErr = updateTransaction(
		transaction,
		transactionSetMemo(request.Metadata[types.MetadataKeyMemo]),
		transactionSetNodeAccountId(nodeAccountId),
		transactionSetTransactionId(payer, validStartNanos),
		transactionSetValidDuration(validDurationSeconds),
		transactionFreeze,
	); rErr != nil {
		return nil, rErr
	}

	bytes, err := hiero.TransactionToBytes(transaction)
	if err != nil {
		return nil, errors.ErrTransactionMarshallingFailed
	}

	frozenBodyBytes, rErr := getFrozenTransactionBodyBytes(transaction)
	if rErr != nil {
		return nil, rErr
	}

	signingPayloads := make([]*rTypes.SigningPayload, 0, len(signers))
	for _, signer := range signers {
		signingPayloads = append(signingPayloads, &rTypes.SigningPayload{
			AccountIdentifier: signer.ToRosetta(),
			Bytes:             frozenBodyBytes,
			SignatureType:     rTypes.Ed25519,
		})
	}

	return &rTypes.ConstructionPayloadsResponse{
		UnsignedTransaction: tools.SafeAddHexPrefix(hex.EncodeToString(bytes)),
		Payloads:            signingPayloads,
	}, nil
}

// ConstructionPreprocess implements the /construction/preprocess endpoint.
func (c *constructionAPIService) ConstructionPreprocess(
	ctx context.Context,
	request *rTypes.ConstructionPreprocessRequest,
) (*rTypes.ConstructionPreprocessResponse, *rTypes.Error) {
	operations, rErr := c.getOperationSlice(request.Operations)
	if rErr != nil {
		return nil, rErr
	}

	signers, err := c.transactionHandler.Preprocess(ctx, operations)
	if err != nil {
		return nil, err
	}

	requiredPublicKeys := make([]*rTypes.AccountIdentifier, 0, len(signers))
	for _, signer := range signers {
		requiredPublicKeys = append(requiredPublicKeys, &rTypes.AccountIdentifier{Address: signer.String()})
	}

	options := make(map[string]interface{})
	if len(request.Metadata) != 0 {
		options = request.Metadata
	}

	options[optionKeyOperationType] = operations[0].Type

	// the first signer is always the payer account
	payer := signers[0]
	if payer.HasAlias() {
		options[optionKeyAccountAliases] = payer.String()
	}

	return &rTypes.ConstructionPreprocessResponse{
		Options:            options,
		RequiredPublicKeys: requiredPublicKeys,
	}, nil
}

// ConstructionSubmit implements the /construction/submit endpoint.
func (c *constructionAPIService) ConstructionSubmit(
	_ context.Context,
	request *rTypes.ConstructionSubmitRequest,
) (*rTypes.TransactionIdentifierResponse, *rTypes.Error) {
	if !c.IsOnline() {
		return nil, errors.ErrEndpointNotSupportedInOfflineMode
	}

	transaction, rErr := unmarshallTransactionFromHexString(request.SignedTransaction)
	if rErr != nil {
		return nil, rErr
	}

	hashBytes, err := hiero.TransactionGetTransactionHash(transaction)
	if err != nil {
		return nil, errors.ErrTransactionHashFailed
	}

	hash := tools.SafeAddHexPrefix(hex.EncodeToString(hashBytes))
	transactionId, _ := hiero.TransactionGetTransactionID(transaction)
	log.Infof("Submitting transaction %s (hash %s) to node %s", transactionId,
		hash, transaction.GetNodeAccountIDs()[0])

	_, err = hiero.TransactionExecute(transaction, c.hederaClient)
	if err != nil {
		log.Errorf("Failed to execute transaction %s (hash %s): %s", transactionId, hash, err)
		return nil, errors.AddErrorDetails(
			errors.ErrTransactionSubmissionFailed,
			"reason",
			fmt.Sprintf("%s", err),
		)
	}

	return &rTypes.TransactionIdentifierResponse{
		TransactionIdentifier: &rTypes.TransactionIdentifier{Hash: hash},
	}, nil
}

func (c *constructionAPIService) getTransactionNodeAccountId(metadata map[string]interface{}) (
	emptyAccountId hiero.AccountID, nilErr *rTypes.Error,
) {
	value, ok := metadata[metadataKeyNodeAccountId]
	if !ok {
		return emptyAccountId, errors.ErrMissingNodeAccountIdMetadata
	}

	str, ok := value.(string)
	if !ok {
		return emptyAccountId, errors.ErrInvalidArgument
	}

	nodeAccountId, err := hiero.AccountIDFromString(str)
	if err != nil {
		log.Errorf("Invalid node account id provided in metadata: %s", str)
		return emptyAccountId, errors.ErrInvalidAccount
	}

	log.Infof("Use node account id %s from metadata", str)
	return nodeAccountId, nilErr
}

func (c *constructionAPIService) getTransactionTimestampProperty(metadata map[string]interface{}) (
	validDurationSeconds, validStartNanos int64, err *rTypes.Error,
) {
	if _, ok := metadata[metadataKeyValidUntilNanos]; ok {
		// ignore valid duration seconds and valid start nanos in the metadata if valid until is present
		var validUntil int64
		validUntil, err = c.getIntMetadataValue(metadata, metadataKeyValidUntilNanos)
		if err != nil {
			log.Errorf("Invalid valid until %s", metadata[metadataKeyValidUntilNanos])
			err = errors.ErrInvalidArgument
			return
		}

		validDurationSeconds = maxValidDurationSeconds
		validStartNanos = validUntil - maxValidDurationNanos
		if validStartNanos <= 0 {
			log.Errorf("Valid start nanos determined from valid until %s is not positive",
				metadata[metadataKeyValidUntilNanos])
			err = errors.ErrInvalidArgument
		}

		return
	}

	validDurationSeconds, err = c.getIntMetadataValue(metadata, metadataKeyValidDurationSeconds)
	if err != nil || !isValidTransactionValidDuration(validDurationSeconds) {
		log.Errorf("Invalid valid duration seconds %s", metadata[metadataKeyValidDurationSeconds])
		err = errors.ErrInvalidArgument
		return
	}

	validStartNanos, err = c.getIntMetadataValue(metadata, metadataKeyValidStartNanos)
	if err != nil {
		log.Errorf("Invalid valid start nanos %s", metadata[metadataKeyValidStartNanos])
	}

	return
}

func (c *constructionAPIService) getOperationSlice(operations []*rTypes.Operation) (
	types.OperationSlice,
	*rTypes.Error,
) {
	operationSlice := make(types.OperationSlice, 0, len(operations))
	for _, operation := range operations {
		var accountId types.AccountId
		if operation.Account != nil {
			var err error
			accountId, err = types.NewAccountIdFromString(operation.Account.Address, c.systemShard, c.systemRealm)
			if err != nil || accountId.IsZero() {
				return nil, errors.ErrInvalidAccount
			}
		}

		var amount types.Amount
		var rErr *rTypes.Error
		if operation.Amount != nil {
			if amount, rErr = types.NewAmount(operation.Amount); rErr != nil {
				return nil, rErr
			}
		}

		operationSlice = append(operationSlice, types.Operation{
			AccountId: accountId,
			Amount:    amount,
			Index:     operation.OperationIdentifier.Index,
			Metadata:  operation.Metadata,
			Type:      operation.Type,
		})
	}

	return operationSlice, nil
}

func (c *constructionAPIService) getSdkPayerAccountId(payerAccountId types.AccountId, accountMapMetadata interface{}) (
	zero hiero.AccountID,
	_ *rTypes.Error,
) {
	if !payerAccountId.HasAlias() {
		accountId, err := payerAccountId.ToSdkAccountId()
		if err != nil {
			return zero, errors.ErrInvalidAccount
		}
		return accountId, nil
	}

	// if it's an alias account, look up the account map metadata for its `shard.realm.num` account id
	if accountMapMetadata == nil {
		return zero, errors.ErrAccountNotFound
	}

	accountMap, ok := accountMapMetadata.(string)
	if !ok {
		return zero, errors.ErrAccountNotFound
	}

	var payer hiero.AccountID
	payerAlias := payerAccountId.String()
	for _, aliasMap := range strings.Split(accountMap, ",") {
		if !strings.HasPrefix(aliasMap, payerAlias) {
			continue
		}

		var err error
		mapping := strings.Split(aliasMap, ":")
		if payer, err = hiero.AccountIDFromString(mapping[1]); err != nil {
			return zero, errors.ErrInvalidAccount
		}
		break
	}

	if payer.Account == 0 {
		return zero, errors.ErrAccountNotFound
	}

	return payer, nil
}

func (c *constructionAPIService) getRandomNodeAccountId() (hiero.AccountID, *rTypes.Error) {
	nodeAccountIds := make([]hiero.AccountID, 0)
	seen := map[hiero.AccountID]struct{}{}
	// network returned from hederaClient is a map[string]AccountID, the key is the address of a node and the value is
	// its node account id. Since a node can have multiple addresses, we need the seen map to get a unique node account
	// id array
	for _, nodeAccountId := range c.hederaClient.GetNetwork() {
		if _, ok := seen[nodeAccountId]; ok {
			continue
		}

		seen[nodeAccountId] = struct{}{}
		nodeAccountIds = append(nodeAccountIds, nodeAccountId)
	}

	if len(nodeAccountIds) == 0 {
		return hiero.AccountID{}, errors.ErrNodeAccountIdsEmpty
	}

	maxValue := big.NewInt(int64(len(nodeAccountIds)))
	index, err := rand.Int(rand.Reader, maxValue)
	if err != nil {
		log.Errorf("Failed to get a random number, use 0 instead: %s", err)
		return nodeAccountIds[0], nil
	}

	return nodeAccountIds[index.Int64()], nil
}

func (c *constructionAPIService) getIntMetadataValue(metadata map[string]interface{}, metadataKey string) (int64, *rTypes.Error) {
	var metadataValue int64
	if metadata != nil && metadata[metadataKey] != nil {
		value, ok := metadata[metadataKey].(string)
		if !ok {
			return metadataValue, errors.ErrInvalidArgument
		}

		var err error
		if metadataValue, err = tools.ToInt64(value); err != nil || metadataValue < 0 {
			return metadataValue, errors.ErrInvalidArgument
		}
	}

	return metadataValue, nil
}

func (c *constructionAPIService) resolveAccountAliases(
	ctx context.Context,
	options map[string]interface{},
) (emptyResult string, nilErr *rTypes.Error) {
	if options[optionKeyAccountAliases] == nil {
		return
	}

	if !c.BaseService.IsOnline() {
		return emptyResult, errors.ErrEndpointNotSupportedInOfflineMode
	}

	accountAliases, ok := options[optionKeyAccountAliases].(string)
	if !ok {
		return emptyResult, errors.ErrInvalidOptions
	}

	var accountMap []string
	for _, accountAlias := range strings.Split(accountAliases, ",") {
		accountId, err := types.NewAccountIdFromString(accountAlias, c.systemShard, c.systemRealm)
		if err != nil {
			return emptyResult, errors.ErrInvalidAccount
		}

		found, rErr := c.accountRepo.GetAccountId(ctx, accountId)
		if rErr != nil {
			return emptyResult, rErr
		}
		accountMap = append(accountMap, fmt.Sprintf("%s:%s", accountAlias, found))
	}

	return strings.Join(accountMap, ","), nilErr
}

func isValidTransactionValidDuration(validDuration int64) bool {
	// A value of 0 indicates validDuration is unset
	return validDuration >= 0 && validDuration <= maxValidDurationSeconds
}

// NewConstructionAPIService creates a new instance of a constructionAPIService.
func NewConstructionAPIService(
	accountRepo interfaces.AccountRepository,
	baseService BaseService,
	config *config.Config,
	transactionConstructor construction.TransactionConstructor,
) (server.ConstructionAPIServicer, error) {
	var err error
	var hederaClient *hiero.Client

	// there is no live demo network, it's only used to run rosetta test, so replace it with testnet
	network := strings.ToLower(config.Network)
	if network == "demo" {
		log.Info("Use testnet instead of demo")
		network = "testnet"
	}

	if len(config.Nodes) > 0 {
		hederaClient = hiero.ClientForNetwork(config.Nodes)
	} else {
		if baseService.IsOnline() {
			hederaClient, err = hiero.ClientForName(network)
		} else {
			// Workaround for offline mode, create client without mirror network to skip the blocking initial network
			// address book update
			clientConfig := []byte(fmt.Sprintf("{\"network\": \"%s\"}", network))
			hederaClient, err = hiero.ClientFromConfig(clientConfig)
		}

		if err != nil {
			return nil, err
		}
	}

	if baseService.IsOnline() && len(config.Nodes) == 0 {
		// Set network update period only when in online mode and there is no network nodes configuration
		hederaClient.SetNetworkUpdatePeriod(config.NodeRefreshInterval)
	}

	// disable SDK auto retry
	hederaClient.SetMaxAttempts(1)

	return &constructionAPIService{
		accountRepo:        accountRepo,
		BaseService:        baseService,
		hederaClient:       hederaClient,
		systemShard:        config.Shard,
		systemRealm:        config.Realm,
		transactionHandler: transactionConstructor,
	}, nil
}

func getFrozenTransactionBodyBytes(transaction hiero.TransactionInterface) ([]byte, *rTypes.Error) {
	signedTransaction := services.SignedTransaction{}
	data, _ := hiero.TransactionString(transaction)
	if err := prototext.Unmarshal([]byte(data), &signedTransaction); err != nil {
		return nil, errors.ErrTransactionUnmarshallingFailed
	}

	return signedTransaction.BodyBytes, nil
}

func unmarshallTransactionFromHexString(transactionString string) (hiero.TransactionInterface, *rTypes.Error) {
	transactionBytes, err := hex.DecodeString(tools.SafeRemoveHexPrefix(transactionString))
	if err != nil {
		return nil, errors.ErrTransactionDecodeFailed
	}

	transaction, err := hiero.TransactionFromBytes(transactionBytes)
	if err != nil {
		return nil, errors.ErrTransactionUnmarshallingFailed
	}

	if rErr := isSupportedTransactionType(transaction); rErr != nil {
		return nil, rErr
	}

	return transaction, nil
}

func isSupportedTransactionType(transaction hiero.TransactionInterface) *rTypes.Error {
	switch transaction.(type) {
	case hiero.AccountCreateTransaction:
	case hiero.TransferTransaction:
	default:
		return errors.ErrTransactionInvalidType
	}

	return nil
}

type updater func(transaction hiero.TransactionInterface) *rTypes.Error

func updateTransaction(transaction hiero.TransactionInterface, updaters ...updater) *rTypes.Error {
	for _, updater := range updaters {
		if err := updater(transaction); err != nil {
			return err
		}
	}
	return nil
}

func transactionSetMemo(memo interface{}) updater {
	return func(transaction hiero.TransactionInterface) *rTypes.Error {
		if memo == nil {
			return nil
		}

		value, ok := memo.(string)
		if !ok {
			return errors.ErrInvalidTransactionMemo
		}

		if _, err := hiero.TransactionSetTransactionMemo(transaction, value); err != nil {
			return errors.ErrInvalidTransactionMemo
		}

		return nil
	}
}

func transactionSetNodeAccountId(nodeAccountId hiero.AccountID) updater {
	return func(transaction hiero.TransactionInterface) *rTypes.Error {
		if _, err := hiero.TransactionSetNodeAccountIDs(transaction, []hiero.AccountID{nodeAccountId}); err != nil {
			log.Errorf("Failed to set node account id for transaction: %s", err)
			return errors.ErrInternalServerError
		}
		return nil
	}
}

func transactionSetTransactionId(payer hiero.AccountID, validStartNanos int64) updater {
	return func(transaction hiero.TransactionInterface) *rTypes.Error {
		var transactionId hiero.TransactionID
		if validStartNanos == 0 {
			transactionId = hiero.TransactionIDGenerate(payer)
		} else {
			transactionId = hiero.NewTransactionIDWithValidStart(payer, time.Unix(0, validStartNanos))
		}
		if _, err := hiero.TransactionSetTransactionID(transaction, transactionId); err != nil {
			log.Errorf("Failed to set transaction id: %s", err)
			return errors.ErrInternalServerError
		}
		return nil
	}
}

func transactionSetValidDuration(validDurationSeconds int64) updater {
	return func(transaction hiero.TransactionInterface) *rTypes.Error {
		if validDurationSeconds == 0 {
			// Default to 180 seconds
			validDurationSeconds = defaultValidDurationSeconds
		}

		if _, err := hiero.TransactionSetTransactionValidDuration(transaction, time.Second*time.Duration(validDurationSeconds)); err != nil {
			log.Errorf("Failed to set transaction valid duration: %s", err)
			return errors.ErrInternalServerError
		}
		return nil
	}
}

func transactionFreeze(transaction hiero.TransactionInterface) *rTypes.Error {
	if _, err := hiero.TransactionFreezeWith(transaction, nil); err != nil {
		log.Errorf("Failed to freeze transaction: %s", err)
		return errors.ErrTransactionFreezeFailed
	}

	return nil
}
