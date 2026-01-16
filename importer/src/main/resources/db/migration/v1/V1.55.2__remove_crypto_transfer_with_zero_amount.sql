-- remove crypto transfers with amount equals 0.

with crypto_creates as (
	select consensus_timestamp
	from transaction
	where type = 11
	  and result = 22
)
delete
from crypto_transfer ct using crypto_creates cc
where ct.consensus_timestamp = cc.consensus_timestamp
  and amount = 0;

