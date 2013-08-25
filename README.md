BitcoinJ Command-Line Interface
===============================

This is a _very_ simple interactive shell for the excellent
[bitcoinj](https://code.google.com/p/bitcoinj/) Java library by [Mike
Hearn](http://plan99.net/~mike/) _et al._ All it does is connect to
the test net and let you examine its state, look in the wallet, and
make payments.

This program is written in Scala.  Run it using sbt like this:

    sbt run

You can specify the filename of a wallet with the `--wallet` option:

    sbt 'run --wallet=<filename>'

If the above line doesn't work then you might have to first start
`sbt` and then give the `run --wallet` command to the `sbt` shell.

If the wallet file does not exist then it will create one with a new
key.  If you do not specify a filename for the wallet then it will be
named `default.wallet`.

Once you're at the command prompt, entering `help` will display the
available commands.

This program generates a logfile, named `bitcoinj.log`, that you can
watch to see what's happening.

Peer Discovery
--------------

This program uses DNS peer discovery, which does not always work well
on the test net.  If you are unable to connect to peers then you can
uncomment the appropriate line in `Server.scala` to use a hard-coded
list of peers rather than DNS discovery.  Or the DNS discovery might
be commented out depending on where I left things.

Bugs
----

If your EC Key doesn't have the private key necessary for signing a
payment then you get a timeout rather than an appropriate message.
(I'm learning to use Akka at the same time I'm learning to use
bitcoinj.)

Warning
=======
This is the first thing I've written using the `bitcoinj` library.
**Not recommended for anything besides experimentation and learning.**

