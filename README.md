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

## Footnote

Thanks to everybody in the Lift Community and on [Liftweb Google Groups](http://groups.google.com/group/liftweb).


## License

```
  Copyright (c) 2011, Franz Bettag <franz@bett.ag>
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met:
     * Redistributions of source code must retain the above copyright
       notice, this list of conditions and the following disclaimer.
     * Redistributions in binary form must reproduce the above copyright
       notice, this list of conditions and the following disclaimer in the
       documentation and/or other materials provided with the distribution.
     * All advertising materials mentioning features or use of this software
       must display the following acknowledgement:
       This product includes software developed by the Bettag Systems UG
       and its contributors.

  THIS SOFTWARE IS PROVIDED BY BETTAG SYSTEMS UG ''AS IS'' AND ANY
  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
  DISCLAIMED. IN NO EVENT SHALL BETTAG SYSTEMS UG BE LIABLE FOR ANY
  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

