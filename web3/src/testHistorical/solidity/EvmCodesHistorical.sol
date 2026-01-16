// SPDX-License-Identifier: Apache-2.0
// Example for precompiles used: https://github.com/jstoxrocky/zksnarks_example

// post 0.8.19 versions of solidity use opcode PUSH0, which is not available for pre-v0.38 EVM
// and the historical calls would fail
pragma solidity 0.8.7;

contract EvmCodesHistorical {

    function getCodeHash(address _address) external view returns (bytes32) {
        bytes32 codehash;
        assembly {
            codehash := extcodehash(_address)
        }
        return codehash;
    }

    function getBlockPrevrandao() external view returns (uint256) {
        return block.difficulty;
    }

    function getBlockTimestamp() external view returns (uint256) {
        return block.timestamp;
    }

    function getLatestBlockHash() public view returns (bytes32) {
        return blockhash(block.number);
    }

    // External view function that retrieves the hbar balance of a given account
    function getAccountBalance(address _owner) external view returns (uint) {
        return _owner.balance;
    }
}