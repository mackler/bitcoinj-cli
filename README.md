BitcoinJ Command-Line Interface
===============================

This is a command-line-interface Bitcoin-wallet application.  It is
built around the excellent
[bitcoinj](https://code.google.com/p/bitcoinj/) Java library by
[Mike Hearn](http://plan99.net/~mike/) _et al._ This application
connects to the Bitcoin test net, and has built-in commands to send
payments, to examine its state, to look in the wallet, and for other
related purposes.

Building
--------

This program is written in Scala and is built using the Simple Build
Tool, [`sbt`](http://www.scala-sbt.org/release/docs/).  After you
clone this repository, simply do:

    sbt start-script

That will compile this application and create an executable shell
script named `start` in the `target` directory.

Running
-------

Once you have built the shell script named `start`, you can run this
application:

    target/start

If a wallet file does not already exist then it will create a new one
with a single key.  Such a default wallet will be named after the
Bitcoin network that you're using, _e.g._, if you are using the test
network, then the wallet file will be named `test.wallet`.  The name
of the block chain file will correspond to the name of the wallet
file.

You can specify the filename of a wallet by using the `--wallet` option.
If the filename you specifiy does not end in `.wallet` then that
suffix will be added to the name of the wallet file that is created or
opened.  If you want to use an existing blockchain file, then its name
must match the wallet filename, but end in `.spvchain` rather than
`.wallet`.

**Peer Discovery**

DNS peer discovery does not always work well on the test net,
wherefore the IP numbers of the test-net peers are hard-coded into the
source.  They may get out-of-date.  If you would rather use DNS peer
discovery, invoke this application with the `dns` command-line
switch:

    target/start --dns

**Choosing the Network**

By default, this application connects to the Bitcoin test network.  To
connect to the main network you must invoke with the `--notest`
switch:

    target/start --notest
	
**Invocation Help**

To display a summary of all command-line options and switches:

    target/start --help

Usage
-----

Once you're at the command prompt, the `help` command will display
descriptions of all the available commands.  This is different than
the command-line `help` switch, which applies only to starting this
application, not using it.

This application generates a logfile, named `bitcoinj.log`, that you can
watch to see what's happening in the background.


Getting the bitcoinj library
----------------------------

For security reasons, the bitcoinj library is not available as a maven
artifact; thus you must build it yourself.  This is easy.
Simply
[follow these instructions](https://code.google.com/p/bitcoinj/wiki/UsingMaven),
beginning where it reads "To get bitcoinj you can use git and check out
your own copy."

As of this writing, this application was built against bitcoinj repository
commit
[32a823804c4f](https://code.google.com/p/bitcoinj/source/detail?r=32a823804c4ff89f89aeff73da42498be84672ee),
which may be different from the one shown in the referenced instructions.

Known Bugs
----------

* The wallet file, block chain file, and log file are read from and put
  into your current working directory.  There is no way to specifiy a
  different directory.

* This application does not limit the size of the log file, which will
  keep growing and must be deleted or moved manually.
  
* If DNS discovery fails you get no message telling you so (except in
  the log).

Caveatis
========

Obviously, it's foolish to use this or any other Bitcoin-wallet
application without taking appropriate precautions to prevent coin
loss.  I have not had any unrecoverable disasters using this
application, but you are responsible for protecting your wallet.
In plain terms: **use at your own risk.**

I welcome feedback:
[AdamMackler@gmail.com](mailto://AdamMackler@gmail.com?subject=Bitcoinj-CLI+Feedback)
