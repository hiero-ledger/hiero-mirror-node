-------------------
-- Support proto updates in 0.13.0-rc.1 with respect to transaction result types
-------------------

-- New transaction result codes
insert into t_transaction_results (proto_id, result)
values (215, 'OPERATION_REPEATED_IN_BUCKET_GROUPS'),
       (216, 'BUCKET_CAPACITY_OVERFLOW'),
       (217, 'NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION'),
       (218, 'BUCKET_HAS_NO_THROTTLE_GROUPS'),
       (219, 'THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC'),
       (220, 'SUCCESS_BUT_MISSING_EXPECTED_OPERATION'),
       (221, 'UNPARSEABLE_THROTTLE_DEFINITIONS'),
       (222, 'INVALID_THROTTLE_DEFINITIONS');
