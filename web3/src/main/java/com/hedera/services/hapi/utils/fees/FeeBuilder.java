// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.hapi.utils.fees;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseType;
import java.math.BigInteger;
import java.util.List;

/**
 * Temporary extracted class from services.
 *
 * <p>This is the base class for building Fee Matrices and calculating the Total as well as specific
 * component Fee for a given Transaction or Query. It includes common methods which is used to calculate Fee for Crypto,
 * File and Smart Contracts Transactions and Query
 *
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. Removed unused methods
 */
public class FeeBuilder {

    public static final int LONG_SIZE = 8;
    public static final int FEE_MATRICES_CONST = 1;
    public static final int INT_SIZE = 4;
    public static final int BOOL_SIZE = 4;
    public static final int KEY_SIZE = 32;
    public static final int TX_HASH_SIZE = 48;
    public static final long RECEIPT_STORAGE_TIME_SEC = 180;
    public static final int FEE_DIVISOR_FACTOR = 1000;
    public static final int HRS_DIVISOR = 3600;
    public static final int BASIC_ENTITY_ID_SIZE = (3 * LONG_SIZE);
    public static final int BASIC_ACCOUNT_SIZE = 8 * LONG_SIZE + BOOL_SIZE;
    public static final int BASIC_ACCOUNT_AMT_SIZE = BASIC_ENTITY_ID_SIZE + LONG_SIZE;
    public static final int BASIC_TX_ID_SIZE = BASIC_ENTITY_ID_SIZE + LONG_SIZE;
    public static final int EXCHANGE_RATE_SIZE = 2 * INT_SIZE + LONG_SIZE;
    public static final int CRYPTO_ALLOWANCE_SIZE = BASIC_ENTITY_ID_SIZE + INT_SIZE + LONG_SIZE; // owner, spender ,
    public static final int TOKEN_ALLOWANCE_SIZE = BASIC_ENTITY_ID_SIZE + 2 * INT_SIZE + LONG_SIZE; // owner, tokenNum,
    public static final int NFT_ALLOWANCE_SIZE = BASIC_ENTITY_ID_SIZE + 2 * INT_SIZE + BOOL_SIZE; // owner, tokenNum,
    public static final int STATE_PROOF_SIZE = 2000;

    public static final int NFT_DELETE_ALLOWANCE_SIZE = 2 * BASIC_ENTITY_ID_SIZE; // owner, tokenID

    public static final int BASIC_RECEIPT_SIZE = INT_SIZE + 2 * EXCHANGE_RATE_SIZE;
    public static final int BASIC_TX_BODY_SIZE =
            BASIC_ENTITY_ID_SIZE + BASIC_TX_ID_SIZE + LONG_SIZE + (LONG_SIZE) + BOOL_SIZE;

    public static final long BASIC_QUERY_RES_HEADER = 2L * INT_SIZE + LONG_SIZE;

    public static final long BASIC_QUERY_HEADER = 212L;
    /**
     * Fields included in size: receipt (basic size), transactionHash, consensusTimestamp, transactionID
     * transactionFee.
     */
    public static final int BASIC_TX_RECORD_SIZE =
            BASIC_RECEIPT_SIZE + TX_HASH_SIZE + LONG_SIZE + BASIC_TX_ID_SIZE + LONG_SIZE;

    protected FeeBuilder() {}

    /**
     * Convert tinyCents to tinybars
     *
     * @param exchangeRate exchange rate
     * @param tinyCentsFee tiny cents fee
     * @return tinyHbars
     */
    public static long getTinybarsFromTinyCents(final ExchangeRate exchangeRate, final long tinyCentsFee) {
        return getAFromB(tinyCentsFee, exchangeRate.getHbarEquiv(), exchangeRate.getCentEquiv());
    }

    /**
     * This method returns the Key size in bytes
     *
     * @param key key
     * @return int representing account key storage size
     */
    public static int getAccountKeyStorageSize(final Key key) {

        if (key == null) {
            return 0;
        }
        if (key == Key.getDefaultInstance()) {
            return 0;
        }

        int[] countKeyMetatData = {0, 0};
        countKeyMetatData = calculateKeysMetadata(key, countKeyMetatData);

        return countKeyMetatData[0] * KEY_SIZE + countKeyMetatData[1] * INT_SIZE;
    }

    /**
     * This method calculates number of keys
     *
     * @param key   key
     * @param count count array
     * @return int array containing key metadata
     */
    public static int[] calculateKeysMetadata(final Key key, int[] count) {
        if (key.hasKeyList()) {
            final List<Key> keyList = key.getKeyList().getKeysList();
            for (final Key value : keyList) {
                count = calculateKeysMetadata(value, count);
            }
        } else if (key.hasThresholdKey()) {
            final List<Key> keyList = key.getThresholdKey().getKeys().getKeysList();
            count[1]++;
            for (final Key value : keyList) {
                count = calculateKeysMetadata(value, count);
            }
        } else {
            count[0]++;
        }
        return count;
    }

    /**
     * This method calculates Fee for specific component (Noe/Network/Service) based upon param
     * componentCoefficients and componentMetrics
     *
     * @param componentCoefficients component coefficients
     * @param componentMetrics compnent metrics
     * @return long representation of the fee in tiny cents
     */
    public static long getComponentFeeInTinyCents(
            final FeeComponents componentCoefficients, final FeeComponents componentMetrics) {

        final long bytesUsageFee = componentCoefficients.getBpt() * componentMetrics.getBpt();
        final long verificationFee = componentCoefficients.getVpt() * componentMetrics.getVpt();
        final long ramStorageFee = componentCoefficients.getRbh() * componentMetrics.getRbh();
        final long storageFee = componentCoefficients.getSbh() * componentMetrics.getSbh();
        final long evmGasFee = componentCoefficients.getGas() * componentMetrics.getGas();
        final long txValueFee = Math.round((float) (componentCoefficients.getTv() * componentMetrics.getTv()) / 1000);
        final long bytesResponseFee = componentCoefficients.getBpr() * componentMetrics.getBpr();
        final long storageBytesResponseFee = componentCoefficients.getSbpr() * componentMetrics.getSbpr();
        final long componentUsage = componentCoefficients.getConstant() * componentMetrics.getConstant();

        long totalComponentFee = componentUsage
                + (bytesUsageFee
                        + verificationFee
                        + ramStorageFee
                        + storageFee
                        + evmGasFee
                        + txValueFee
                        + bytesResponseFee
                        + storageBytesResponseFee);

        if (totalComponentFee < componentCoefficients.getMin()) {
            totalComponentFee = componentCoefficients.getMin();
        } else if (totalComponentFee > componentCoefficients.getMax()) {
            totalComponentFee = componentCoefficients.getMax();
        }
        return Math.max(totalComponentFee > 0 ? 1 : 0, (totalComponentFee) / FEE_DIVISOR_FACTOR);
    }

    public static FeeObject getFeeObject(
            final FeeData feeData, final FeeData feeMatrices, final ExchangeRate exchangeRate, final long multiplier) {
        // get the Network Fee
        long networkFee = getComponentFeeInTinyCents(feeData.getNetworkdata(), feeMatrices.getNetworkdata());
        long nodeFee = getComponentFeeInTinyCents(feeData.getNodedata(), feeMatrices.getNodedata());
        long serviceFee = getComponentFeeInTinyCents(feeData.getServicedata(), feeMatrices.getServicedata());
        // convert the Fee to tiny hbars
        networkFee = FeeBuilder.getTinybarsFromTinyCents(exchangeRate, networkFee) * multiplier;
        nodeFee = FeeBuilder.getTinybarsFromTinyCents(exchangeRate, nodeFee) * multiplier;
        serviceFee = FeeBuilder.getTinybarsFromTinyCents(exchangeRate, serviceFee) * multiplier;
        return new FeeObject(nodeFee, networkFee, serviceFee);
    }

    /**
     * Get fee object
     *
     * @param feeData fee data
     * @param feeMatrices fee matrices
     * @param exchangeRate exchange rate
     * @return fee object
     */
    public static FeeObject getFeeObject(
            final FeeData feeData, final FeeData feeMatrices, final ExchangeRate exchangeRate) {
        return getFeeObject(feeData, feeMatrices, exchangeRate, 1L);
    }

    public static int getStateProofSize(final ResponseType responseType) {
        return (responseType == ResponseType.ANSWER_STATE_PROOF || responseType == ResponseType.COST_ANSWER_STATE_PROOF)
                ? STATE_PROOF_SIZE
                : 0;
    }

    public static FeeData getQueryFeeDataMatrices(final FeeComponents feeComponents) {

        final FeeComponents feeMatricesForTxService = FeeComponents.getDefaultInstance();

        final FeeComponents feeMatricesForTxNetwork = FeeComponents.getDefaultInstance();

        final FeeComponents feeMatricesForTxNode = FeeComponents.newBuilder()
                .setConstant(FEE_MATRICES_CONST)
                .setBpt(feeComponents.getBpt())
                .setBpr(feeComponents.getBpr())
                .setSbpr(feeComponents.getSbpr())
                .build();

        return FeeData.newBuilder()
                .setNetworkdata(feeMatricesForTxNetwork)
                .setNodedata(feeMatricesForTxNode)
                .setServicedata(feeMatricesForTxService)
                .build();
    }

    private static long getAFromB(final long bAmount, final int aEquiv, final int bEquiv) {
        final var aMultiplier = BigInteger.valueOf(aEquiv);
        final var bDivisor = BigInteger.valueOf(bEquiv);
        return BigInteger.valueOf(bAmount)
                .multiply(aMultiplier)
                .divide(bDivisor)
                .longValueExact();
    }
}
