// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

contract EquivalenceContract {
    address constant HTS_PRECOMPILE_ADDRESS = address(0x167);
    address constant EXCHANGE_RATE_PRECOMPILE_ADDRESS = address(0x168);
    address constant PRNG_PRECOMPILE_ADDRESS = address(0x169);

    function makeCallWithoutAmount(address _to, bytes memory _data) external returns (bool success, bytes memory returnData) {
        (success, returnData) = _to.call(_data);
    }

    function makeCallWithAmount(address _to, bytes memory _data) external payable returns (bool success, bytes memory returnData) {
        (success, returnData) = _to.call{value: msg.value}(_data);
    }

    function makeStaticCall(address _to, bytes memory _data) external returns (bool success, bytes memory returnData) {
        (success, returnData) = _to.staticcall(_data);
    }

    function makeDelegateCall(address _to, bytes memory _data) external returns (bool success, bytes memory returnData) {
        (success, returnData) = _to.delegatecall(_data);
    }

    function callCodeToContractWithoutAmount(address _addr, bytes calldata _customData) external returns (bytes32 output) {
        assembly {
            let x := mload(0x40)
            calldatacopy(x, _customData.offset, calldatasize())

            let success := callcode(
                900000, // gas
                _addr, // target address
                0, // value
                x, // extracted and loaded calldata for the internal call
                calldatasize(), // as far as the data is dynamic, we can use calldatasize() to get the variable size
                x, // memory overlapping to save some space
                0x20 // output is 32 bytes long
            )

            output := mload(x) // assign output value to the var
        }
    }

    function callCodeToContractWithAmount(address _addr, bytes calldata _customData) external payable returns (bytes32 output) {
        assembly {
            let x := mload(0x40)
            calldatacopy(x, _customData.offset, calldatasize())
            let success := callcode(
                900000, // gas
                _addr, // target address
                callvalue(), // value
                x, // extracted and loaded calldata for the internal call
                calldatasize(), // as far as the data is dynamic, we can use calldatasize() to get the variable size
                x, // memory overlapping to save some space
                0x20 // output is 32 bytes long
            )

            output := mload(x) // assign output value to the var
        }
    }

    function getBalance(address _address) external view returns (uint256) {
        return _address.balance;
    }

    function getCodeSize(address _address) external view returns (uint256) {
        uint256 size;
        assembly {
            size := extcodesize(_address)
        }
        return size;
    }

    function getCodeHash(address _address) external view returns (bytes32) {
        bytes32 codehash;
        assembly {
            codehash := extcodehash(_address)
        }
        return codehash;
    }

    function copyCode(address _address) external view returns (bytes memory) {
        uint256 size;
        assembly {
            size := extcodesize(_address)
        }
        bytes memory code = new bytes(size);

        assembly {
            extcodecopy(_address, add(code, 32), 0, size)
        }
        return code;
    }

    function getPseudorandomSeed() external returns (bytes32 randomBytes) {
        (bool success, bytes memory result) = PRNG_PRECOMPILE_ADDRESS.call(
            abi.encodeWithSignature("getPseudorandomSeed()"));
        require(success);
        randomBytes = abi.decode(result, (bytes32));
    }

    function getPseudorandomSeedWithAmount() external payable returns (bytes32 randomBytes) {
        (bool success, bytes memory result) = PRNG_PRECOMPILE_ADDRESS.call{value: msg.value}(
            abi.encodeWithSignature("getPseudorandomSeed()"));
        require(success);
        randomBytes = abi.decode(result, (bytes32));
    }

    function exchangeRateWithoutAmount(uint256 tinycents) external returns (uint256 tinybars) {
        (bool success, bytes memory result) = EXCHANGE_RATE_PRECOMPILE_ADDRESS.call(
            abi.encodeWithSignature("tinycentsToTinybars(uint256)", tinycents));
        require(success);
        tinybars = abi.decode(result, (uint256));
    }

    function exchangeRateWithAmount(uint256 tinycents) external payable returns (uint256 tinybars) {
        (bool success, bytes memory result) = EXCHANGE_RATE_PRECOMPILE_ADDRESS.call{value: msg.value}(
            abi.encodeWithSignature("tinycentsToTinybars(uint256)", tinycents));
        require(success);
        tinybars = abi.decode(result, (uint256));
    }

    function getCurrentAddress() external returns(address){
        return address(this);
    }
}
