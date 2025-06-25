// SPDX-License-Identifier: Apache-2.0
// Example for precompiles used: https://github.com/jstoxrocky/zksnarks_example

// post 0.8.19 versions of solidity use opcode PUSH0, which is not available for pre-v0.38 EVM
// and the historical calls would fail
pragma solidity >=0.5.0 <0.9.0;

contract EvmCodes {

    struct G1Point {
        uint X;
        uint Y;
    }
    // Encoding of field elements is: X[0] * z + X[1]
    struct G2Point {
        uint[2] X;
        uint[2] Y;
    }
    /// @return the generator of G1
    function P1() internal pure returns (G1Point memory) {
        return G1Point(1, 2);
    }
    /// @return the generator of G2
    function P2() internal pure returns (G2Point memory) {
        return G2Point(
            [11559732032986387107991004021392285783925812861821192530917403151452391805634,
            10857046999023057135944570762232829481370756359578518086990519993285655852781],
            [4082367875863433681332203403145435568316851327593401208105741076214120093531,
            8495653923123431417604973247489272438418190587263600148770280649306958101930]
        );
    }
    /// @return the negation of p, i.e. p.add(p.negate()) should be zero.
    function negate(G1Point memory p) internal pure returns (G1Point memory) {
        // The prime q in the base field F_q for G1
        uint q = 21888242871839275222246405745257275088696311157297823662689037894645226208583;
        if (p.X == 0 && p.Y == 0)
            return G1Point(0, 0);
        return G1Point(p.X, q - (p.Y % q));
    }


    function chainId() public view returns (uint256) {
        return block.chainid;
    }

    function recoverAddress() public returns (address recoveredAddress) {
        bytes32 hash = bytes32("hash");
        uint8 v = uint8(1);
        bytes32 r = bytes32("r");
        bytes32 s = bytes32("s");
        (bool success, bytes memory result) = address(0x01).call(
            abi.encodeWithSignature("ecrecover(bytes32,uint8,bytes32,bytes32)", hash, v, r, s));
        require(success);

        return address(bytes20(result));
    }

    function calculateSHA256() public returns (bytes memory) {
        bytes memory input = abi.encodePacked("Hello, world!");

        (bool success, bytes memory result) = address(0x02).call(
            abi.encode(input));

        require(success);
        return result;
    }

    function calculateRIPEMD160() public returns (bytes memory) {
        bytes memory input = abi.encode("Hello, RIPEMD-160!");

        (bool success, bytes memory result) = address(0x03).call(
            abi.encode(input));

        require(success);
        return result;
    }

    function identity() public returns (bytes memory) {
        bytes memory input = abi.encode("Hello, World");

        (bool success, bytes memory result) = address(0x04).call(
            abi.encode(input));

        require(success);

        return result;
    }

    function modExp() public returns (uint256 result) {
        uint256 base = 5;
        uint256 exponent = 3;
        uint256 modulus = 11;

        assembly {
        // Free memory pointer
            let pointer := mload(0x40)

        // Define length of base, exponent and modulus. 0x20 == 32 bytes
            mstore(pointer, 0x20)
            mstore(add(pointer, 0x20), 0x20)
            mstore(add(pointer, 0x40), 0x20)

        // Define variables base, exponent and modulus
            mstore(add(pointer, 0x60), base)
            mstore(add(pointer, 0x80), exponent)
            mstore(add(pointer, 0xa0), modulus)

        // Store the result
            let value := mload(0xc0)

        // Call the precompiled contract 0x05 = bigModExp
            if iszero(call(not(0), 0x05, 0, pointer, 0xc0, value, 0x20)) {
                revert(0, 0)
            }

            result := mload(value)
        }
    }

    // Function to add two points on the elliptic curve using the AltBN128AddPrecompiledContract.
    function addPoints() public returns (G1Point memory r) {
        G1Point memory p1 = P1();
        G1Point memory p2 = P1();
        uint256[4] memory input;
        input[0] = p1.X;
        input[1] = p1.Y;
        input[2] = p2.X;
        input[3] = p2.Y;
        bool success;
        assembly {
            success := call(sub(gas(), 2000), 0x06, 0, input, 0xc0, r, 0x60)
        // Use "invalid" to make gas estimation work
            switch success case 0 {invalid()}
        }
        require(success);
    }

    // Function to perform multiplication using AltBN128MulPrecompiledContract.
    function multiplyPoints() public returns (G1Point memory r) {
        G1Point memory p = P1();
        uint[3] memory input;
        input[0] = p.X;
        input[1] = p.Y;
        input[2] = 3; //scalar value used for multiplication
        bool success;
        assembly {
            success := call(sub(gas(), 2000), 0x07, 0, input, 0x80, r, 0x60)
        // Use "invalid" to make gas estimation work
            switch success case 0 {invalid()}
        }
        require(success);
    }

    // Function to perform a pairing check using AltBN128PairingPrecompiledContract.
    function pairingCheck() public returns (bool) {
        G1Point[2] memory p1 = [P1(), negate(P1())];
        G2Point[2] memory p2 = [P2(), P2()];

        require(p1.length == p2.length);
        uint elements = p1.length;
        uint inputSize = elements * 6;
        uint[] memory input = new uint[](inputSize);
        for (uint i = 0; i < elements; i++)
        {
            input[i * 6 + 0] = p1[i].X;
            input[i * 6 + 1] = p1[i].Y;
            input[i * 6 + 2] = p2[i].X[0];
            input[i * 6 + 3] = p2[i].X[1];
            input[i * 6 + 4] = p2[i].Y[0];
            input[i * 6 + 5] = p2[i].Y[1];
        }

        uint[1] memory out;
        bool success;
        assembly {
            success := call(sub(gas(), 2000), 8, 0, add(input, 0x20), mul(inputSize, 0x20), out, 0x20)
        // Use "invalid" to make gas estimation work
            switch success case 0 {invalid()}
        }
        require(success);
        return out[0] != 0;
    }

    // Function to perform a blake2 compression using BLAKE2BFPrecompileContract.
    function blake2() public view returns (bytes32[2] memory output) {
        uint32 rounds = 12;

        bytes32[2] memory h;
        h[0] = hex"48c9bdf267e6096a3ba7ca8485ae67bb2bf894fe72f36e3cf1361d5f3af54fa5";
        h[1] = hex"d182e6ad7f520e511f6c3e2b8c68059b6bbd41fbabd9831f79217e1319cde05b";

        bytes32[4] memory m;
        m[0] = hex"6162630000000000000000000000000000000000000000000000000000000000";
        m[1] = hex"0000000000000000000000000000000000000000000000000000000000000000";
        m[2] = hex"0000000000000000000000000000000000000000000000000000000000000000";
        m[3] = hex"0000000000000000000000000000000000000000000000000000000000000000";

        bytes8[2] memory t;
        t[0] = hex"03000000";
        t[1] = hex"00000000";

        bool f = true;

        bytes memory args = abi.encodePacked(rounds, h[0], h[1], m[0], m[1], m[2], m[3], t[0], t[1], f);

        assembly {
            if iszero(staticcall(not(0), 0x09, add(args, 32), 0xd5, output, 0x40)) {
                revert(0, 0)
            }
        }
    }

    function getBlockPrevrandao() external view returns (uint256) {
        return block.prevrandao;
    }

    function getBlockTimestamp() external view returns (uint256) {
        return block.timestamp;
    }

    function getBlockHash(uint256 blockNumber) public view returns (bytes32) {
        return blockhash(blockNumber);
    }

    function getLatestBlockHash() public view returns (bytes32) {
        return blockhash(block.number);
    }

    function getCodeHash(address _address) external view returns (bytes32) {
        bytes32 codehash;
        assembly {
            codehash := extcodehash(_address)
        }
        return codehash;
    }

    function destroyContract(address payable beneficiary) public {
        selfdestruct(beneficiary);
    }

    function tryKZGPrecompile() public view {
        bytes memory byts = "Hello, World!";
        address(0x0A).staticcall(byts);
    }

    function tryTransientStorage() public {
        assembly {
            tstore(10, 11111)
            pop(tload(10))
        }
    }

    function tryMcopy() public pure {
        bytes memory byts = "Hello, World!";
        assembly {
            mcopy(0, 0, 32)
        }
    }
}