// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;

interface IPrngSystemContractHistorical {
    // Generates a 256-bit pseudorandom seed using the first 256-bits of running hash from the latest RecordFile in the database.
    // Users can generate a pseudorandom number in a specified range using the seed by (integer value of seed % range)
    function getPseudorandomSeed() external returns (bytes32);
}
