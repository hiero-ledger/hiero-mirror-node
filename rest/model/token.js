// SPDX-License-Identifier: Apache-2.0

class Token {
  static tableAlias = 't';
  static tableName = 'token';
  static CREATED_TIMESTAMP = 'created_timestamp';
  static DECIMALS = 'decimals';
  static FEE_SCHEDULE_KEY = 'fee_schedule_key';
  static FREEZE_DEFAULT = 'freeze_default';
  static FREEZE_KEY = 'freeze_key';
  static FREEZE_STATUS = 'freeze_status';
  static INITIAL_SUPPLY = 'initial_supply';
  static KYC_KEY = 'kyc_key';
  static KYC_STATUS = 'kyc_status';
  static MAX_SUPPLY = 'max_supply';
  static METADATA = 'metadata';
  static METADATA_KEY = 'metadata_key';
  static NAME = 'name';
  static PAUSE_KEY = 'pause_key';
  static PAUSE_STATUS = 'pause_status';
  static SUPPLY_KEY = 'supply_key';
  static SUPPLY_TYPE = 'supply_type';
  static SYMBOL = 'symbol';
  static TIMESTAMP_RANGE = `timestamp_range`;
  static TOKEN_ID = `token_id`;
  static TOTAL_SUPPLY = 'total_supply';
  static TREASURY_ACCOUNT_ID = 'treasury_account_id';
  static TYPE = 'type';
  static WIPE_KEY = 'wipe_key';
  static TYPES = {
    FUNGIBLE_COMMON: 'FUNGIBLE_COMMON',
    NON_FUNGIBLE_UNIQUE: 'NON_FUNGIBLE_UNIQUE',
  };

  /**
   * Parses token table columns into object
   */
  constructor(token) {
    this.createdTimestamp = BigInt(token.created_timestamp);
    this.deleted = token.deleted;
    this.decimals = BigInt(token.decimals);
    this.feeScheduleKey = token.fee_schedule_key;
    this.freezeDefault = token.freeze_default;
    this.freezeKey = token.freeze_key;
    this.initialSupply = BigInt(token.initial_supply);
    this.kycKey = token.kyc_key;
    this.maxSupply = BigInt(token.max_supply);
    this.memo = token.memo;
    this.metadata = token.metadata;
    this.metadataKey = token.metadata_key;
    this.modifiedTimestamp = BigInt(token.modified_timestamp);
    this.name = token.name;
    this.pauseKey = token.pause_key;
    this.pauseStatus = token.pause_status;
    this.supplyKey = token.supply_key;
    this.supplyType = token.supply_type;
    this.symbol = token.symbol;
    this.tokenId = token.token_id;
    this.totalSupply = BigInt(token.total_supply);
    this.treasuryAccountId = token.treasury_account_id;
    this.type = token.type;
    this.wipeKey = token.wipe_key;
  }

  /**
   * Gets full column name with table alias prepended.
   *
   * @param {string} columnName
   * @private
   */
  static getFullName(columnName) {
    return `${this.tableAlias}.${columnName}`;
  }
}

export default Token;
