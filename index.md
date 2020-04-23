## What is BX-bot?

<img src="https://raw.githubusercontent.com/gazbert/bxbot/master/docs/bxbot-cropped.png" align="right" width="25%" />

BX-bot (_Bex_) is a simple [Bitcoin](https://bitcoin.org) trading bot written in Java for trading on cryptocurrency 
[exchanges](https://bitcoin.org/en/exchanges).

The project contains the basic infrastructure to trade on a [cryptocurrency](http://coinmarketcap.com/) exchange...
except for the trading strategies - you'll need to write those yourself! A simple 
[example](./bxbot-strategies/src/main/java/com/gazbert/bxbot/strategies/ExampleScalpingStrategy.java) of a 
[scalping](http://www.investopedia.com/articles/trading/02/081902.asp) strategy is included to get you started with the
Trading API - take a look [here](https://github.com/ta4j/ta4j) for more ideas.

Exchange Adapters for using [Bitstamp](https://www.bitstamp.net), [Bitfinex](https://www.bitfinex.com),
[itBit](https://www.itbit.com/), [Kraken](https://www.kraken.com), and [Gemini](https://gemini.com/) are included.
Feel free to improve these or contribute new adapters to the project; that would be 
[shiny!](https://en.wikipedia.org/wiki/Firefly_(TV_series))

The Trading API provides support for [limit orders](http://www.investopedia.com/terms/l/limitorder.asp)
traded at the [spot price](http://www.investopedia.com/terms/s/spotprice.asp).
If you're looking for something more sophisticated with a much richer Trading API, take a look at
[XChange](https://github.com/knowm/XChange).
 
**Warning:** Trading Bitcoin carries significant financial risk; you could lose money. This software is provided 'as is'
and released under the [MIT license](http://opensource.org/licenses/MIT).
