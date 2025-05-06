// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

/**
 * Child contract of parent
 * Supports receiving of funds and ability to spend and report its balance
 *
 */
contract Child {
    address owner;

    constructor() {
        owner = msg.sender;
    }

    // This function is based on hedera-services Create2Factory.deploy()
    function vacateAddress() public {
        emit Create2Vacate(address(this));
        selfdestruct(payable(owner));
    }

    receive() external payable {
        emit Transfer(msg.sender, address(this), msg.value);
    }

    event Transfer(address indexed _from, address indexed _to, uint256 amount);
    event Create2Vacate(address addr);
}

/**
 * Contract to showcase a parent child contract relationship
 * On creation parent creates one sub child contract. Parent can create at most one more child for simplicity.
 * Parent supports a payable constructor and donate function to supply additional funds to children.
 * On creation of second child a transfer amount cna be specified to fund second child contract
 * Parent contract supports retrieval of address and balance of child contracts
 *
 */
contract Parent {
    address[2] childAddresses;

    constructor() payable {
        Child firstChild = new Child();
        childAddresses[0] = address(firstChild);
        emit ParentActivityLog("Created first child contract");
    }

    function createChild(uint256 amount) public returns (address) {
        childAddresses[1] = address(new Child());
        emit ParentActivityLog("Created second child contract");

        if (amount > 0) {
            payable(childAddresses[1]).transfer(amount);
        }

        return childAddresses[1];
    }

    function getAccountBalance() external view returns (uint) {
        return childAddresses[1].balance;
    }

    function getSender() external view returns (address) {
        return msg.sender;
    }

    function multiplySimpleNumbers() public pure returns (uint) {
        return 2 * 2;
    }

    function identifier() public pure returns (bytes4) {
        return msg.sig;
    }

    receive() external payable {}

    // This function was sourced from hedera-services Create2Factory.sol
    function getBytecode() public pure returns (bytes memory) {
        bytes memory bytecode = type(Child).creationCode;

        return abi.encodePacked(bytecode);
    }

    // This function was sourced from hedera-services Create2Factory.sol
    function getAddress(bytes memory bytecode, uint _salt) public view returns (address)
    {
        bytes32 hash = keccak256(
            abi.encodePacked(bytes1(0xff), address(this), _salt, keccak256(bytecode))
        );

        // NOTE: cast last 20 bytes of hash to address
        return address(uint160(uint(hash)));
    }

    // This function was sourced from hedera-services Create2Factory.deploy()
    function create2Deploy(bytes memory bytecode, uint _salt) public payable {
        address addr;

        /*
        NOTE: How to call create2

        create2(v, p, n, s)
        create new contract with code at memory p to p + n
        and send v wei
        and return the new address
        where new address = first 20 bytes of keccak256(0xff + address(this) + s + keccak256(mem[p…(p+n)))
              s = big-endian 256-bit value
        */
        assembly {
            addr := create2(
            callvalue(), // wei sent with current call
            // Actual code starts after skipping the first 32 bytes
            add(bytecode, 0x20),
            mload(bytecode), // Load the size of code contained in the first 32 bytes
            _salt // Salt from function arguments
            )

            if iszero(extcodesize(addr)) {
                revert(0, 0)
            }
        }

        emit Create2Deploy(addr);
    }

    event ParentActivityLog(string message);
    event Create2Deploy(address addr);
}
