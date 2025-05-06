---
--- Add new topic_message indexes to enable initial rest api for individual topic messages
---

create unique index if not exists topic_message__topic_num_realm_num_seqnum
    on topic_message (realm_num, topic_num, sequence_number);
