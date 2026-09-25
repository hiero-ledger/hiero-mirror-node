// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.10;

contract BLSPrecompileCall {

    function staticCallBLSAddress(address _addr, bytes memory _input) external view returns (bool success, bytes memory result) {
        (success, result) = _addr.staticcall(_input);
    }

    function callBLSAddress(address _addr, bytes memory _input) external returns (bool success, bytes memory result) {
        (success, result) = _addr.call(_input);
    }
}
