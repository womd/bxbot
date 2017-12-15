## What is BX-bot?

<img src="https://raw.githubusercontent.com/gazbert/bxbot/master/docs/bxbot-cropped.png" align="right" width="25%" />

BX-bot (_Bex_) is a simple [Bitcoin](https://bitcoin.org) trading bot written in Java for trading on cryptocurrency 
[exchanges](https://bitcoinwisdom.com/).

The project contains the basic infrastructure to trade on a [cryptocurrency](http://coinmarketcap.com/) exchange...
except for the trading strategies - you'll need to write those yourself! A simple example
[scalping strategy](http://www.investopedia.com/articles/trading/02/081902.asp) is included to get you started with the
Trading API - take a look [here](http://www.investopedia.com/articles/active-trading/101014/basics-algorithmic-trading-concepts-and-examples.asp)
for more ideas.

Exchange Adapters for using [Bitstamp](https://www.bitstamp.net), [Bitfinex](https://www.bitfinex.com),
[OKCoin](https://www.okcoin.com/), [GDAX](https://www.gdax.com/),
[itBit](https://www.itbit.com/), [Kraken](https://www.kraken.com), and [Gemini](https://gemini.com/) are included. 
Feel free to improve these or contribute new adapters to the project; that would be [shiny!](https://en.wikipedia.org/wiki/Firefly_(TV_series))

The Trading API provides support for [limit orders](http://www.investopedia.com/terms/l/limitorder.asp)
traded at the [spot price](http://www.investopedia.com/terms/s/spotprice.asp);
it does not support [futures](http://www.investopedia.com/university/beginners-guide-to-trading-futures/) or 
[margin](http://www.investopedia.com/university/margin/) trading.
 
**Warning:** Trading Bitcoin carries significant financial risk; you could lose money. This software is provided 'as is'
and released under the [MIT license](http://opensource.org/licenses/MIT).
