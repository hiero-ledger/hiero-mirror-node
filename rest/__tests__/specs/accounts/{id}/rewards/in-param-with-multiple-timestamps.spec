{
  "description": "Account staking reward payouts api call testing IN with multiple timestamps",
  "setup": {
    "accounts": [
      {
        "num": 1001
      }
    ],
    "stakingRewardTransfers": [
      {
        "account_id": "0.0.1001",
        "amount": 10,
        "consensus_timestamp": "1234567890000011111"
      },
      {
        "account_id": "0.0.1001",
        "amount": 12,
        "consensus_timestamp": "1234567890000022222"
      },
      {
        "account_id": "0.0.1001",
        "amount": 13,
        "consensus_timestamp": "1234567890000033333"
      },
      {
        "account_id": "0.0.1001",
        "amount": 14,
        "consensus_timestamp": "1234567890000044444"
      },
      {
        "account_id": "0.0.1001",
        "amount": 15,
        "consensus_timestamp": "1234567890000055555"
      }
    ]
  },
  "url": "/api/v1/accounts/1001/rewards?timestamp=1234567890.000011111&timestamp=1234567890.000033333&&timestamp=1234567890.000055555",
  "responseStatus": 200,
  "responseJson": {
    "rewards": [
      {
        "account_id": "0.0.1001",
        "amount": 10,
        "timestamp": "1234567890.000011111"
      },
      {
        "account_id": "0.0.1001",
        "amount": 13,
        "timestamp": "1234567890.000033333"
      },
      {
        "account_id": "0.0.1001",
        "amount": 15,
        "timestamp": "1234567890.000055555"
      }
    ],
    "links": {
      "next": null
    }
  }
}
