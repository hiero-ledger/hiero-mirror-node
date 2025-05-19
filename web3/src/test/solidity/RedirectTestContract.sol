// SPDX-License-Identifier: MIT
pragma solidity ^0.8.9;

import "./HederaTokenService.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC20/extensions/IERC20Metadata.sol";
import "@openzeppelin/contracts/token/ERC721/IERC721.sol";
import "@openzeppelin/contracts/token/ERC721/extensions/IERC721Metadata.sol";


contract RedirectTestContract is HederaTokenService {

    function nameRedirect(address token) public returns (bytes memory result) {
        (int response, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC20Metadata.name.selector));
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token name redirect failed");
        }
        return responseResult;
    }

    function symbolRedirect(address token) public returns (bytes memory result) {
        (int response, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC20Metadata.symbol.selector));
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token symbol redirect failed");
        }
        return responseResult;
    }

    function decimalsRedirect(address token) public returns (bytes memory result) {
        (int response, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC20Metadata.decimals.selector));
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token decimals() redirect failed");
        }
        return responseResult;
    }

    function totalSupplyRedirect(address token) external returns (bytes memory result) {
        (int response, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC20.totalSupply.selector));
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token totalSupply redirect failed");
        }
        return responseResult;
    }

    function balanceOfRedirect(address token, address account) public returns (bytes memory result) {
        (int response, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC20.balanceOf.selector, account));
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token balanceOf redirect failed");
        }
        return responseResult;
    }

    function allowanceRedirect(address token, address owner, address spender) external returns (bytes memory result) {
        (int response, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC20.allowance.selector, owner, spender));
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token allowance redirect failed");
        }
        return responseResult;
    }

    function getApprovedRedirect(address token, uint256 tokenId) external returns (bytes memory result) {
        (int response, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC721.getApproved.selector, tokenId));
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token getApproved redirect failed");
        }
        return responseResult;
    }

    function getOwnerOfRedirect(address token, uint256 serialNo) external returns (bytes memory result) {
        (int response, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC721.ownerOf.selector, serialNo));
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token getOwnerOf redirect failed");
        }
        return responseResult;
    }

    function tokenURIRedirect(address token, uint256 tokenId) public returns (bytes memory result) {
        (int response, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC721Metadata.tokenURI.selector, tokenId));
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token tokenURI redirect failed");
        }
        return responseResult;
    }

    function isApprovedForAllRedirect(address token, address owner, address operator) public returns (bytes memory result) {
        (int response, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC721.isApprovedForAll.selector, owner, operator));
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token isApprovedForAll redirect failed");
        }
        return responseResult;
    }

    //Modification operations

    function transferRedirect(address token, address recipient, uint256 amount) public returns (bytes memory result) {
        (int response, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC20.transfer.selector, recipient, amount));
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token transfer redirect failed");
        }
        return responseResult;
    }

    function transferFromRedirect(address token, address sender, address recipient, uint256 amount) public returns (bytes memory result) {
        (int response, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC20.transferFrom.selector, sender, recipient, amount));
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token transferFrom redirect failed");
        }
        return responseResult;
    }

    function approveRedirect(address token, address spender, uint256 amount) public returns (bytes memory result) {
        (int response, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC20.approve.selector, spender, amount));
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token approve redirect failed");
        }
        return responseResult;
    }

    function transferFromNFTRedirect(address token, address from, address to, uint256 tokenId) public returns (bytes memory result) {
        (int response, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC721.transferFrom.selector, from, to, tokenId));
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token transferFromNFT redirect failed");
        }
        return responseResult;
    }

}