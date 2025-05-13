-------------------
-- delete token transfers transaction type
-------------------

-- Delete transaction type TOKENTRANSFERS (30)
delete from t_transaction_types where proto_id = 30;
