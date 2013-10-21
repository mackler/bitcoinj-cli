BitcoinJ Command-Line Interface
===============================

This is a simple interactive shell user interface for the excellent
[bitcoinj](https://code.google.com/p/bitcoinj/), which is a Java
library by [Mike Hearn](http://plan99.net/~mike/) _et al._ This
application uses `bitcoinj` to connect to the Bitcoin test net, to
receive and to send payments, and has other commands for examining its
state and looking in the wallet.

This program is written in Scala and is built using the Simple Build
Tool, [`sbt`](http://www.scala-sbt.org/release/docs/).  After you
clone this repository, simply do:

    sbt start-script

That will compile the source code and then create an executable shell
script named `start` in the `target` directory.  Then, to run this
application:

    target/start

If a wallet file does not already exist then it will create a new one
with a single key.

You can specify the filename of a wallet with the `--wallet` option.
If the filename you specifiy does not end in `.wallet` then that
suffix will be added to the name of the wallet file that is created or
opened.  Any blockchain filename must match the wallet filename, but
end in `.spvchain` rather than `.wallet`.

Of course, you can also use the standard sbt `run` task rather than
`start-script`, if you want.

Usage
-----

Once you're at the command prompt, entering `help` will display the
available commands.

This program generates a logfile, named `bitcoinj.log`, that you can
watch to see what's happening in the background.

Peer Discovery
--------------

DNS peer discovery does not always work well on the test net,
wherefore the IP numbers of the test peers are hard-coded into the
source.  They may get out-of-date.  If you want to switch to DNS peer
discovery, simply comment and uncomment the appropriate lines in
`Server.scala`.

Getting the bitcoinj library
----------------------------

For security reasons, the bitcoinj library is not available as a maven
artifact; thus you must build it yourself.  This is not difficult.
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

* The wallet file, block chain file, and log file are put into the
  current working directory.

* This application does not limit the size of the log file, which will
  keep growing and must be moved or deleted manually.

* If your EC Key doesn't have the private key necessary for signing a
  payment then you get a timeout rather than an appropriate message.
  (I'm learning to use Akka at the same time I'm learning to use
  bitcoinj.)

Caveatis
========

Obviously, it's foolish to use this or any other Bitcoin-wallet
application without taking appropriate precautions to prevent coin
loss.  I have not had any unrecoverable disasters using this
application, but you are responsible for backing up your wallet file.
In plain terms: **use at your own risk.**

I welcome feedback:
[AdamMackler@gmail.com](mailto://AdamMackler@gmail.com?subject=Bitcoinj-CLI+Feedback)
