-------------------
-- Add new response codes for network freeze
-------------------

insert into t_transaction_results (result, proto_id)
values ('FREEZE_UPDATE_FILE_DOES_NOT_EXIST', 268),
       ('FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH', 269),
       ('NO_UPGRADE_HAS_BEEN_PREPARED', 270),
       ('NO_FREEZE_IS_SCHEDULED', 271),
       ('UPDATE_FILE_HASH_CHANGED_SINCE_PREPARE_UPGRADE', 272),
       ('FREEZE_START_TIME_MUST_BE_FUTURE', 273),
       ('PREPARED_UPDATE_FILE_IS_IMMUTABLE', 274),
       ('FREEZE_ALREADY_SCHEDULED', 275),
       ('FREEZE_UPGRADE_IN_PROGRESS', 276),
       ('UPDATE_FILE_ID_DOES_NOT_MATCH_PREPARED', 277),
       ('UPDATE_FILE_HASH_DOES_NOT_MATCH_PREPARED', 278);
