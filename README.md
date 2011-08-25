CoinMines
=========


Installation
------------

You need the following components installed:

- [PostgreSQL](http://www.postgresql.org)
- [Scala](http://www.scala-lang.org)
- [pushpool](https://github.com/MtRed/pushpool) from MtRed with PostgreSQL Support (and it's dependencies)


Update dependencies with sbt:

<code>./sbt update</code>


Update run jetty:

<code>./sbt ~jetty-run</code>


PostgreSQL Console:

<code>
INSERT INTO settings (setting, value) VALUES
	('current_workers', 0),
	('current_hashrate', 0),
	('current_users', 0),
	('current_roundshares', 0),
	('sitepercent', 2.0);
</code>


In pushpool's server.json:

<code>
	"sharelog" : true,
	"stmt.pwdb" : "SELECT password FROM pool_worker WHERE username = $1",
	"stmt.sharelog" : "INSERT INTO shares (rem_host, username, our_result, upstream_result, reason, solution, timestamp_c) values ($1, $2, $3, $4, $5, decode($6, 'hex'), NOW())"
</code>
