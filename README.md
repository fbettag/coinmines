CoinMines
=========


Installation
------------

You need the following components installed:

- [PostgreSQL](http://www.postgresql.org)
- [Scala](http://www.scala-lang.org)
- [pushpool](https://github.com/MtRed/pushpool) from MtRed with PostgreSQL Support (and it's dependencies)


Update dependencies with sbt:

```
./sbt update
```


Update run jetty:

```
./sbt ~jetty-run
```


PostgreSQL Console:

```
INSERT INTO settings (setting, value) VALUES
	('current_workers', 0),
	('current_hashrate', 0),
	('current_users', 0),
	('current_roundshares', 0),
	('sitepercent', 2.0);
```


Start your bitcoin server in a screen/tmux session:

```
./bitcoin-0.3.xx/bin/64/bitcoind -server
```


Get and compile pushpool:

```
git clone https://github.com/MtRed/pushpool.git
cd pushpool
./configure --prefix=/opt/pushpool
make
make install
```


In /opt/pushpool/server.json:

```
	"sharelog" : true,
	"stmt.pwdb" : "SELECT password FROM pool_worker WHERE username = $1",
	"stmt.sharelog" : "INSERT INTO shares (rem_host, username, our_result, upstream_result, reason, solution, timestamp_c) values ($1, $2, $3, $4, $5, decode($6, 'hex'), NOW())"
```


Start pushpools blkmond in a separate screen/tmux window:

```
/opt/pushpool/sbin/blkmond blkmon.cfg
```


Start pushpool itself (also in a new window):

```
/opt/pushpool/sbin/pushpoold --stderr -F
```





