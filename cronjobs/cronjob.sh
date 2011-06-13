#!/bin/sh

DBUSER=pushpool
DBNAME=pushpool

BLOCKNUM=$( bitcoind getblocknumber )
DIFFICULTY=$( bitcoind getdifficulty )

# Updating current block
psql -q -U$DBUSER $DBNAME <<EOF
INSERT INTO network_blocks (blocknumber, timestamp_c, confirms)
	SELECT '$BLOCKNUM', NOW(), 0 WHERE NOT EXISTS (SELECT blocknumber FROM network_blocks WHERE blocknumber = '$BLOCKNUM');
EOF

# Updating hashrate
psql -q -U$DBUSER $DBNAME <<EOF
UPDATE pool_worker AS worker SET
	hashrate = (
		SELECT (COUNT(share.id) * 4294967296)/600/1000000
		FROM shares AS share WHERE worker.username = share.username AND share.timestamp_c >= NOW() - interval '10 minutes'
	),
	lasthash = (
		SELECT MAX(share.timestamp_c) FROM shares AS share WHERE worker.username = share.username
	);
EOF

# Updating sharecounts
psql -q -U$DBUSER $DBNAME <<EOF
UPDATE users SET
	shares_total = (
		SELECT COUNT(*) FROM shares share WHERE share.username LIKE users.email || '_%' GROUP BY users.id
		UNION
		SELECT COUNT(*) FROM shares_history AS share WHERE users.id = share.user_c GROUP BY users.id
	),
	shares_round = (
		SELECT COUNT(*) FROM shares AS share WHERE share.username LIKE users.email || '_%' GROUP BY users.id
	),
	shares_stale = (
		SELECT COUNT(*) FROM shares AS share WHERE share.username LIKE users.email || '_%' AND share.our_result = false GROUP BY users.id
		UNION
		SELECT COUNT(*) FROM shares_history AS share WHERE users.id = share.user_c AND share.our_result = false GROUP BY users.id
	),
	hashrate = (
		SELECT SUM(hashrate) FROM pool_worker AS worker WHERE users.id = worker.user_c
	);
EOF

# Updating workercount
psql -q -U$DBUSER $DBNAME <<EOF
UPDATE settings SET value = (
	SELECT COUNT(*) FROM pool_worker WHERE lasthash > NOW() - interval '10 minutes'
) WHERE setting = 'current_workers';
EOF

# Updating hashrate
psql -q -U$DBUSER $DBNAME <<EOF
UPDATE settings SET value = (
SELECT SUM(hashrate) FROM pool_worker WHERE lasthash > NOW() - interval '10 minutes'
) WHERE setting = 'current_hashrate';
EOF

# Updating usercount
psql -q -U$DBUSER $DBNAME <<EOF
UPDATE settings SET value = (SELECT COUNT(*) FROM users) WHERE setting = 'current_users';
EOF

# Updating totalshares this round
psql -q -U$DBUSER $DBNAME <<EOF
UPDATE settings SET value = (SELECT SUM(shares_round) FROM users) WHERE setting = 'current_roundshares';
EOF

# $sitePercentQ = mysql_query("SELECT value FROM settings WHERE setting='sitepercent'");
# if ($sitePercentR = mysql_fetch_object($sitePercentQ)) $sitePercent = $sitePercentR->value;				
#
# //Setup score variables
# $c = .001;
# $f=1;
# if ($sitePercent > 0)
#	$f = $sitePercent / 100;
# else
#	$f = (-$c)/(1-$c);
#
# $p = 1.0/$difficulty;
# $r = log(1.0-$p+$p/$c);
# $B = 50;
# $los = log(1/(exp($r)-1));
#
# //Proportional estimate
# $totalRoundShares = $settings->getsetting("currentroundshares");
# if ($totalRoundShares < $difficulty) $totalRoundShares = $difficulty;
#	$userListQ = mysql_query("UPDATE webUsers SET round_estimate = (1-".$f.")*50*(shares_this_round/".$totalRoundShares.")*(1-(donate_percent/100))");

