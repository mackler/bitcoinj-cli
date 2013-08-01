BitcoinJ Command-Line Interface
===============================

This is a _very_ simple interactive shell for the excellent
[bitcoinj](https://code.google.com/p/bitcoinj/) Java library by
[Mike Hearn](http://plan99.net/~mike/) _et al._ All it does is connect
to the test net and let you examine its state and make payments.

This program is written in Scala.  Run it using sbt like this:

    sbt run

You can specify the filename of a wallet with the `--wallet` option:

    sbt 'run --wallet=<filename>'

If the wallet file does not exist then it will create one with a new
key.  If you do not specify a filename for the wallet then it will be
named `test.wallet`.

Once you're at the command prompt, entering `help` will display the
available commands.

This program generates a logfile, named `bitcoinj.log`, that you can
watch to see what's happening.

Warning
-------
This is the first thing I've written using the `bitcoinj` library.
**Not recommended for anything besides experimentation and learning.**

