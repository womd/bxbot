package com.gazbert.bxbot.exchanges;

import com.gazbert.bxbot.exchange.api.AuthenticationConfig;
import com.gazbert.bxbot.exchange.api.ExchangeAdapter;
import com.gazbert.bxbot.exchange.api.ExchangeConfig;
import com.gazbert.bxbot.exchange.api.OtherConfig;
import com.gazbert.bxbot.exchanges.trading.api.impl.*;
import com.gazbert.bxbot.trading.api.*;
import com.google.common.base.MoreObjects;
import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.*;

public class CryptoComExchangeAdapter extends AbstractExchangeAdapter
implements ExchangeAdapter {

    private static final Logger LOG = LogManager.getLogger();

    private static final String CRYPTOCOM_BASE_URI_TEST = "https://uat-api.3ona.co/v2/";
    private static final String CRYPTOCOM_BASE_URI_PROD = "https://api.crypto.com/v2/";
    private static final String CRYPTOCOM_API_VERSION = "v2";
    private static final String CRYPTOCOM_PUBLIC_PATH = "/public/";
    private static final String CRYPTOCOM_PRIVATE_PATH = "/private/";
    private static final String PUBLIC_API_BASE_URL =
            CRYPTOCOM_BASE_URI_TEST + CRYPTOCOM_API_VERSION + CRYPTOCOM_PUBLIC_PATH;
    private static final String AUTHENTICATED_API_URL =
            CRYPTOCOM_BASE_URI_TEST + CRYPTOCOM_API_VERSION + CRYPTOCOM_PRIVATE_PATH;

    private static final String KEY_PROPERTY_NAME = "key";
    private static final String SECRET_PROPERTY_NAME = "secret";

    private static final String BUY_FEE_PROPERTY_NAME = "buy-fee";
    private static final String SELL_FEE_PROPERTY_NAME = "sell-fee";

    private static final String KEEP_ALIVE_DURING_MAINTENANCE_PROPERTY_NAME =
            "keep-alive-during-maintenance";
    private static final String EXCHANGE_UNDERGOING_MAINTENANCE_RESPONSE = "EService:Unavailable";

    private static final String UNEXPECTED_ERROR_MSG =
            "Unexpected error has occurred in Kraken Exchange Adapter. ";
    private static final String UNEXPECTED_IO_ERROR_MSG =
            "Failed to connect to Exchange due to unexpected IO error.";

    private static final String UNDER_MAINTENANCE_WARNING_MESSAGE =
            "Exchange is undergoing maintenance - keep alive is" + " true.";
    private static final String FAILED_TO_GET_MARKET_ORDERS =
            "Failed to get Market Order Book from exchange. Details: ";
    private static final String FAILED_TO_GET_BALANCE =
            "Failed to get Balance from exchange. Details: ";
    private static final String FAILED_TO_GET_TICKER =
            "Failed to get Ticker from exchange. Details: ";

    private static final String FAILED_TO_GET_OPEN_ORDERS =
            "Failed to get Open Orders from exchange. Details: ";
    private static final String FAILED_TO_ADD_ORDER = "Failed to Add Order on exchange. Details: ";
    private static final String FAILED_TO_CANCEL_ORDER =
            "Failed to Cancel Order on exchange. Details: ";

    private static final String PRICE = "price";

    private long nonce = 0;
    private BigDecimal buyFeePercentage;
    private BigDecimal sellFeePercentage;
    private boolean keepAliveDuringMaintenance;

    private String key = "";
    private String secret = "";

    private Mac mac;
    private boolean initializedMacAuthentication = false;
    private Gson gson;

    @Override
    public void init(ExchangeConfig config) {
        LOG.info(() -> "About to initialise Crypto.com ExchangeConfig: " + config);
        setAuthenticationConfig(config);
        setNetworkConfig(config);
        setOtherConfig(config);

        nonce = System.currentTimeMillis() / 1000;
        initSecureMessageLayer();
        initGson();
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public String getImplName() { return ("Crypto.com API v2"); }

    @Override
    public MarketOrderBook getMarketOrders(String marketId) throws ExchangeNetworkException, TradingApiException {
        ExchangeHttpResponse response;

        try {
            final Map<String, String> params = createRequestParamMap();
            params.put("pair", marketId);

            response = sendPublicRequestToExchange("Depth", params);
            LOG.debug(() -> "Market Orders response: " + response);

            if (response.getStatusCode() == HttpURLConnection.HTTP_OK) {
                final Type resultType =
                        new TypeToken<CryptoComExchangeAdapter.CryptoComResponse<CryptoComExchangeAdapter.CryptoComMarketOrderBookResult>>() {}.getType();
                final CryptoComExchangeAdapter.CryptoComResponse krakenResponse = gson.fromJson(response.getPayload(), resultType);

                final List errors = krakenResponse.error;
                if (errors == null || errors.isEmpty()) {
                    return adaptKrakenOrderBook(krakenResponse, marketId);

                } else {
                    if (isExchangeUndergoingMaintenance(response) && keepAliveDuringMaintenance) {
                        LOG.warn(() -> UNDER_MAINTENANCE_WARNING_MESSAGE);
                        throw new ExchangeNetworkException(UNDER_MAINTENANCE_WARNING_MESSAGE);
                    }

                    final String errorMsg = FAILED_TO_GET_MARKET_ORDERS + response;
                    LOG.error(errorMsg);
                    throw new TradingApiException(errorMsg);
                }

            } else {
                final String errorMsg = FAILED_TO_GET_MARKET_ORDERS + response;
                LOG.error(errorMsg);
                throw new TradingApiException(errorMsg);
            }

        } catch (ExchangeNetworkException | TradingApiException e) {
            throw e;

        } catch (Exception e) {
            LOG.error(UNEXPECTED_ERROR_MSG, e);
            throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
        }
    }

    @Override
    public List<OpenOrder> getYourOpenOrders(String marketId) throws ExchangeNetworkException, TradingApiException {
        ExchangeHttpResponse response;

        try {
            response = sendAuthenticatedRequestToExchange("OpenOrders", null);
            LOG.debug(() -> "Open Orders response: " + response);

            if (response.getStatusCode() == HttpURLConnection.HTTP_OK) {

                final Type resultType = new TypeToken<CryptoComExchangeAdapter.CryptoComResponse<CryptoComExchangeAdapter.CryptoComOpenOrderResult>>() {}.getType();
                final CryptoComExchangeAdapter.CryptoComResponse cryptoComResponse = gson.fromJson(response.getPayload(), resultType);

                final List errors = cryptoComResponse.error;
                if (errors == null || errors.isEmpty()) {
                    return adaptCryptoComOpenOrders(cryptoComResponse, marketId);

                } else {
                    if (isExchangeUndergoingMaintenance(response) && keepAliveDuringMaintenance) {
                        LOG.warn(() -> UNDER_MAINTENANCE_WARNING_MESSAGE);
                        throw new ExchangeNetworkException(UNDER_MAINTENANCE_WARNING_MESSAGE);
                    }

                    final String errorMsg = FAILED_TO_GET_OPEN_ORDERS + response;
                    LOG.error(errorMsg);
                    throw new TradingApiException(errorMsg);
                }

            } else {
                final String errorMsg = FAILED_TO_GET_OPEN_ORDERS + response;
                LOG.error(errorMsg);
                throw new TradingApiException(errorMsg);
            }

        } catch (ExchangeNetworkException | TradingApiException e) {
            throw e;

        } catch (Exception e) {
            LOG.error(UNEXPECTED_ERROR_MSG, e);
            throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
        }
    }

    @Override
    public String createOrder(String marketId, OrderType orderType, BigDecimal quantity, BigDecimal price) throws ExchangeNetworkException, TradingApiException {
        ExchangeHttpResponse response;

        try {
            final Map<String, String> params = createRequestParamMap();
            params.put("pair", marketId);

            if (orderType == OrderType.BUY) {
                params.put("type", "buy");
            } else if (orderType == OrderType.SELL) {
                params.put("type", "sell");
            } else {
                final String errorMsg =
                        "Invalid order type: "
                                + orderType
                                + " - Can only be "
                                + OrderType.BUY.getStringValue()
                                + " or "
                                + OrderType.SELL.getStringValue();
                LOG.error(errorMsg);
                throw new IllegalArgumentException(errorMsg);
            }

            params.put("ordertype", "limit"); // this exchange adapter only supports limit orders
            params.put(PRICE, new DecimalFormat("#.########", getDecimalFormatSymbols()).format(price));
            params.put(
                    "volume", new DecimalFormat("#.########", getDecimalFormatSymbols()).format(quantity));

            response = sendAuthenticatedRequestToExchange("AddOrder", params);
            LOG.debug(() -> "Create Order response: " + response);

            if (response.getStatusCode() == HttpURLConnection.HTTP_OK) {

                final Type resultType = new TypeToken<CryptoComExchangeAdapter.CryptoComResponse<CryptoComExchangeAdapter.CryptoComAddOrderResult>>() {}.getType();
                final CryptoComExchangeAdapter.CryptoComResponse cryptoComResponse = gson.fromJson(response.getPayload(), resultType);

                final List errors = cryptoComResponse.error;
                if (errors == null || errors.isEmpty()) {

                    // Assume we'll always get something here if errors array is empty; else blow fast wih NPE
                    final CryptoComExchangeAdapter.CryptoComAddOrderResult cryptoComAddOrderResult =
                            (CryptoComExchangeAdapter.CryptoComAddOrderResult) cryptoComResponse.result;

                    // Just return the first one. Why an array?
                    return cryptoComAddOrderResult.txid.get(0);

                } else {
                    if (isExchangeUndergoingMaintenance(response) && keepAliveDuringMaintenance) {
                        LOG.warn(() -> UNDER_MAINTENANCE_WARNING_MESSAGE);
                        throw new ExchangeNetworkException(UNDER_MAINTENANCE_WARNING_MESSAGE);
                    }

                    final String errorMsg = FAILED_TO_ADD_ORDER + response;
                    LOG.error(errorMsg);
                    throw new TradingApiException(errorMsg);
                }

            } else {
                final String errorMsg = FAILED_TO_ADD_ORDER + response;
                LOG.error(errorMsg);
                throw new TradingApiException(errorMsg);
            }

        } catch (ExchangeNetworkException | TradingApiException e) {
            throw e;

        } catch (Exception e) {
            LOG.error(UNEXPECTED_ERROR_MSG, e);
            throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
        }
    }

    @Override
    public boolean cancelOrder(String orderId, String marketId) throws ExchangeNetworkException, TradingApiException {
        ExchangeHttpResponse response;

        try {
            final Map<String, String> params = createRequestParamMap();
            params.put("txid", orderId);

            response = sendAuthenticatedRequestToExchange("CancelOrder", params);
            LOG.debug(() -> "Cancel Order response: " + response);

            if (response.getStatusCode() == HttpURLConnection.HTTP_OK) {

                final Type resultType =
                        new TypeToken<CryptoComExchangeAdapter.CryptoComResponse<CryptoComExchangeAdapter.CryptoComCancelOrderResult>>() {}.getType();
                final CryptoComExchangeAdapter.CryptoComResponse cryptoComResponse = gson.fromJson(response.getPayload(), resultType);

                final List errors = cryptoComResponse.error;
                if (errors == null || errors.isEmpty()) {
                    return adaptKrakenCancelOrderResult(cryptoComResponse);

                } else {
                    if (isExchangeUndergoingMaintenance(response) && keepAliveDuringMaintenance) {
                        LOG.warn(() -> UNDER_MAINTENANCE_WARNING_MESSAGE);
                        throw new ExchangeNetworkException(UNDER_MAINTENANCE_WARNING_MESSAGE);
                    }

                    final String errorMsg = FAILED_TO_CANCEL_ORDER + response;
                    LOG.error(errorMsg);
                    throw new TradingApiException(errorMsg);
                }

            } else {
                final String errorMsg = FAILED_TO_CANCEL_ORDER + response;
                LOG.error(errorMsg);
                throw new TradingApiException(errorMsg);
            }

        } catch (ExchangeNetworkException | TradingApiException e) {
            throw e;

        } catch (Exception e) {
            LOG.error(UNEXPECTED_ERROR_MSG, e);
            throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
        }
    }

    @Override
    public BigDecimal getLatestMarketPrice(String marketId) throws ExchangeNetworkException, TradingApiException {
        ExchangeHttpResponse response;

        try {
            final Map<String, String> params = createRequestParamMap();
            params.put("pair", marketId);

            response = sendPublicRequestToExchange("Ticker", params);
            LOG.debug(() -> "Latest Market Price response: " + response);

            if (response.getStatusCode() == HttpURLConnection.HTTP_OK) {

                final Type resultType = new TypeToken<CryptoComExchangeAdapter.CryptoComResponse<CryptoComExchangeAdapter.CryptoComTickerResult>>() {}.getType();
                final CryptoComExchangeAdapter.CryptoComResponse cryptoComResponse = gson.fromJson(response.getPayload(), resultType);

                final List errors = cryptoComResponse.error;
                if (errors == null || errors.isEmpty()) {

                    // Assume we'll always get something here if errors array is empty; else blow fast wih NPE
                    final CryptoComExchangeAdapter.CryptoComTickerResult tickerResult = (CryptoComExchangeAdapter.CryptoComTickerResult) cryptoComResponse.result;

                    // 'c' key into map is the last market price: last trade closed array(<price>, <lot
                    // volume>)
                    return new BigDecimal(tickerResult.get("c"));

                } else {

                    if (isExchangeUndergoingMaintenance(response) && keepAliveDuringMaintenance) {
                        LOG.warn(() -> UNDER_MAINTENANCE_WARNING_MESSAGE);
                        throw new ExchangeNetworkException(UNDER_MAINTENANCE_WARNING_MESSAGE);
                    }

                    final String errorMsg = FAILED_TO_GET_TICKER + response;
                    LOG.error(errorMsg);
                    throw new TradingApiException(errorMsg);
                }

            } else {
                final String errorMsg = FAILED_TO_GET_TICKER + response;
                LOG.error(errorMsg);
                throw new TradingApiException(errorMsg);
            }

        } catch (ExchangeNetworkException | TradingApiException e) {
            throw e;
        } catch (Exception e) {
            LOG.error(UNEXPECTED_ERROR_MSG, e);
            throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
        }
    }

    @Override
    public BalanceInfo getBalanceInfo() throws ExchangeNetworkException, TradingApiException {
        ExchangeHttpResponse response;

        try {
            response = sendAuthenticatedRequestToExchange("Balance", null);
            LOG.debug(() -> "Balance Info response: " + response);

            if (response.getStatusCode() == HttpURLConnection.HTTP_OK) {
                final Type resultType = new TypeToken<CryptoComExchangeAdapter.CryptoComResponse<CryptoComExchangeAdapter.CryptoComBalanceResult>>() {}.getType();
                return adaptCryptoComBalanceInfo(response, resultType);

            } else {
                final String errorMsg = FAILED_TO_GET_BALANCE + response;
                LOG.error(errorMsg);
                throw new TradingApiException(errorMsg);
            }

        } catch (ExchangeNetworkException | TradingApiException e) {
            throw e;

        } catch (Exception e) {
            LOG.error(UNEXPECTED_ERROR_MSG, e);
            throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
        }
    }

    @Override
    public BigDecimal getPercentageOfBuyOrderTakenForExchangeFee(String marketId) throws TradingApiException, ExchangeNetworkException {
        return buyFeePercentage;
    }

    @Override
    public BigDecimal getPercentageOfSellOrderTakenForExchangeFee(String marketId) throws TradingApiException, ExchangeNetworkException {
        return sellFeePercentage;
    }

    @Override
    public Ticker getTicker(String marketId) throws TradingApiException, ExchangeNetworkException {
        ExchangeHttpResponse response;

        try {
            final Map<String, String> params = createRequestParamMap();
            params.put("pair", marketId);

            response = sendPublicRequestToExchange("Ticker", params);
            LOG.debug(() -> "Ticker response: " + response);

            if (response.getStatusCode() == HttpURLConnection.HTTP_OK) {

                final Type resultType = new TypeToken<CryptoComExchangeAdapter.CryptoComResponse<CryptoComExchangeAdapter.CryptoComTickerResult>>() {}.getType();
                final CryptoComExchangeAdapter.CryptoComResponse krakenResponse = gson.fromJson(response.getPayload(), resultType);

                final List errors = krakenResponse.error;
                if (errors == null || errors.isEmpty()) {

                    // Assume we'll always get something here if errors array is empty; else blow fast wih NPE
                    final CryptoComExchangeAdapter.CryptoComTickerResult tickerResult = (CryptoComExchangeAdapter.CryptoComTickerResult) krakenResponse.result;

                    // ouch!
                    return new TickerImpl(
                            new BigDecimal(tickerResult.get("c")), // last trade
                            new BigDecimal(tickerResult.get("b")), // bid
                            new BigDecimal(tickerResult.get("a")), // ask
                            new BigDecimal(tickerResult.get("l")), // low 24h
                            new BigDecimal(tickerResult.get("h")), // high 24hr
                            new BigDecimal(tickerResult.get("o")), // open
                            new BigDecimal(tickerResult.get("v")), // volume 24hr
                            new BigDecimal(tickerResult.get("p")), // vwap 24hr
                            null); // timestamp not supplied by Kraken

                } else {
                    if (isExchangeUndergoingMaintenance(response) && keepAliveDuringMaintenance) {
                        LOG.warn(() -> UNDER_MAINTENANCE_WARNING_MESSAGE);
                        throw new ExchangeNetworkException(UNDER_MAINTENANCE_WARNING_MESSAGE);
                    }

                    final String errorMsg = FAILED_TO_GET_TICKER + response;
                    LOG.error(errorMsg);
                    throw new TradingApiException(errorMsg);
                }

            } else {
                final String errorMsg = FAILED_TO_GET_TICKER + response;
                LOG.error(errorMsg);
                throw new TradingApiException(errorMsg);
            }

        } catch (ExchangeNetworkException | TradingApiException e) {
            throw e;

        } catch (Exception e) {
            LOG.error(UNEXPECTED_ERROR_MSG, e);
            throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
        }
    }


    // --------------------------------------------------------------------------
    //  GSON classes for JSON responses.
    //  See https://exchange-docs.crypto.com/
    // --------------------------------------------------------------------------

    /**
     * GSON base class for all Crypto.com responses.
     *
     * <p>All Crypto responses have the following format:
     *
     * <pre>
     *
     * error = array of error messages in the format of:
     *
     * {char-severity code}{string-error category}:{string-error type}[:{string-extra info}]
     *    - severity code can be E for error or W for warning
     *
     * result = result of API call (may not be present if errors occur)
     *
     * </pre>
     *
     * <p>The result Type is what varies with each API call.
     */
    private static class CryptoComResponse<T> {

        List<String> error;
        T result;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("error", error).add("result", result).toString();
        }
    }

    /** GSON class that wraps Depth API call result - the Market Order Book. */
    private static class CryptoComMarketOrderBookResult extends HashMap<String, CryptoComExchangeAdapter.CryptoComOrderBook> {

        private static final long serialVersionUID = -4913711010647027421L;
    }

    /** GSON class that wraps a Balance API call result. */
    private static class CryptoComBalanceResult extends HashMap<String, BigDecimal> {

        private static final long serialVersionUID = -4919711010647027759L;
    }

    /** GSON class that wraps a Ticker API call result. */
    private static class CryptoComTickerResult extends HashMap<String, String> {

        private static final long serialVersionUID = -4913711010547027759L;

        CryptoComTickerResult() {
        }
    }

    /** GSON class that wraps an Open Order API call result - your open orders. */
    private static class CryptoComOpenOrderResult {

        Map<String, CryptoComExchangeAdapter.CryptoComOpenOrder> open;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("open", open).toString();
        }
    }

    /** GSON class the represents a Kraken Open Order. */
    private static class CryptoComOpenOrder {

        String refid;
        String userref;
        String status;
        double opentm;
        double starttm;
        double expiretm;
        CryptoComExchangeAdapter.CryptoComOpenOrderDescription descr;
        BigDecimal vol;

        @SerializedName("vol_exec")
        BigDecimal volExec;

        BigDecimal cost;
        BigDecimal fee;
        BigDecimal price;
        String misc;
        String oflags;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("refid", refid)
                    .add("userref", userref)
                    .add("status", status)
                    .add("opentm", opentm)
                    .add("starttm", starttm)
                    .add("expiretm", expiretm)
                    .add("descr", descr)
                    .add("vol", vol)
                    .add("volExec", volExec)
                    .add("cost", cost)
                    .add("fee", fee)
                    .add(PRICE, price)
                    .add("misc", misc)
                    .add("oflags", oflags)
                    .toString();
        }
    }

    /** GSON class the represents a CryptoCom Open Order description. */
    private static class CryptoComOpenOrderDescription {

        String pair;
        String type;
        String ordertype;
        BigDecimal price;
        BigDecimal price2;
        String leverage;
        String order;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("pair", pair)
                    .add("type", type)
                    .add("ordertype", ordertype)
                    .add(PRICE, price)
                    .add("price2", price2)
                    .add("leverage", leverage)
                    .add("order", order)
                    .toString();
        }
    }

    /** GSON class representing an AddOrder result. */
    private static class CryptoComAddOrderResult {

        CryptoComExchangeAdapter.CryptoComAddOrderResultDescription descr;
        List<String> txid; // why is this a list/array?

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("descr", descr).add("txid", txid).toString();
        }
    }

    /** GSON class representing an AddOrder result description. */
    private static class CryptoComAddOrderResultDescription {

        String order;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("order", order).toString();
        }
    }

    /** GSON class representing a CancelOrder result. */
    private static class CryptoComCancelOrderResult {

        int count;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("count", count).toString();
        }
    }

    /** GSON class for a Market Order Book. */
    private static class CryptoComOrderBook {

        List<CryptoComExchangeAdapter.CryptoComMarketOrder> bids;
        List<CryptoComExchangeAdapter.CryptoComMarketOrder> asks;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("bids", bids).add("asks", asks).toString();
        }
    }

    /**
     * GSON class for holding Market Orders. First element in array is price, second element is
     * amount, 3rd is UNIX time.
     */
    private static class CryptoComMarketOrder extends ArrayList<BigDecimal> {

        private static final long serialVersionUID = -4959711260742077759L;
    }

    /**
     * Custom GSON Deserializer for Ticker API call result.
     *
     * <p>Have to do this because last entry in the Ticker param map is a String, not an array like
     * the rest of 'em!
     */
    private static class CryptoComTickerResultDeserializer
            implements JsonDeserializer<CryptoComExchangeAdapter.CryptoComTickerResult> {

        CryptoComTickerResultDeserializer() {
        }

        public CryptoComExchangeAdapter.CryptoComTickerResult deserialize(
                JsonElement json, Type type, JsonDeserializationContext context) {

            final CryptoComExchangeAdapter.CryptoComTickerResult cryptoComTickerResult = new CryptoComExchangeAdapter.CryptoComTickerResult();
            if (json.isJsonObject()) {

                final JsonObject jsonObject = json.getAsJsonObject();

                // assume 1 (KV) entry as per API spec - the K is the market id, the V is a Map of ticker
                // params
                final JsonElement tickerParams = jsonObject.entrySet().iterator().next().getValue();

                final JsonObject tickerMap = tickerParams.getAsJsonObject();
                for (Map.Entry<String, JsonElement> jsonTickerParam : tickerMap.entrySet()) {

                    final String key = jsonTickerParam.getKey();
                    switch (key) {
                        case "c":
                            final List<String> lastTradeDetails =
                                    context.deserialize(jsonTickerParam.getValue(), List.class);
                            cryptoComTickerResult.put("c", lastTradeDetails.get(0));
                            break;

                        case "b":
                            final List<String> bidDetails =
                                    context.deserialize(jsonTickerParam.getValue(), List.class);
                            cryptoComTickerResult.put("b", bidDetails.get(0));
                            break;

                        case "a":
                            final List<String> askDetails =
                                    context.deserialize(jsonTickerParam.getValue(), List.class);
                            cryptoComTickerResult.put("a", askDetails.get(0));
                            break;

                        case "l":
                            final List<String> lowDetails =
                                    context.deserialize(jsonTickerParam.getValue(), List.class);
                            cryptoComTickerResult.put("l", lowDetails.get(1));
                            break;

                        case "h":
                            final List<String> highDetails =
                                    context.deserialize(jsonTickerParam.getValue(), List.class);
                            cryptoComTickerResult.put("h", highDetails.get(1));
                            break;

                        case "o":
                            final String openDetails =
                                    context.deserialize(jsonTickerParam.getValue(), String.class);
                            cryptoComTickerResult.put("o", openDetails);
                            break;

                        case "v":
                            final List<String> volumeDetails =
                                    context.deserialize(jsonTickerParam.getValue(), List.class);
                            cryptoComTickerResult.put("v", volumeDetails.get(1));
                            break;

                        case "p":
                            final List<String> vWapDetails =
                                    context.deserialize(jsonTickerParam.getValue(), List.class);
                            cryptoComTickerResult.put("p", vWapDetails.get(1));
                            break;

                        default:
                            LOG.warn(() -> "Received unexpected Ticker param - ignoring: " + key);
                    }
                }
            }
            return cryptoComTickerResult;
        }
    }


    // --------------------------------------------------------------------------
    //  Transport layer methods
    // --------------------------------------------------------------------------

    private ExchangeHttpResponse sendPublicRequestToExchange(
            String apiMethod, Map<String, String> params)
            throws ExchangeNetworkException, TradingApiException {

        if (params == null) {
            params = createRequestParamMap(); // no params, so empty query string
        }

        // Request headers required by Exchange
        final Map<String, String> requestHeaders = createHeaderParamMap();

        try {
            final StringBuilder queryString = new StringBuilder();
            if (!params.isEmpty()) {
                queryString.append("?");
                for (final Map.Entry<String, String> param : params.entrySet()) {
                    if (queryString.length() > 1) {
                        queryString.append("&");
                    }
                    queryString.append(param.getKey());
                    queryString.append("=");
                    queryString.append(URLEncoder.encode(param.getValue(), StandardCharsets.UTF_8));
                }

                requestHeaders.put("Content-Type", "application/x-www-form-urlencoded");
            }

            final URL url = new URL(PUBLIC_API_BASE_URL + apiMethod + queryString);
            return makeNetworkRequest(url, "GET", null, requestHeaders);

        } catch (MalformedURLException e) {
            final String errorMsg = UNEXPECTED_IO_ERROR_MSG;
            LOG.error(errorMsg, e);
            throw new TradingApiException(errorMsg, e);
        }
    }

    /*
     * Makes an authenticated API call to the Kraken exchange.
     *
     * Kraken requires the following HTTP headers to bet set:
     *
     * API-Key = API key
     * API-Sign = Message signature using HMAC-SHA512 of (URI path + SHA256(nonce + POST data))
     *            and base64 decoded secret API key
     *
     * The nonce must always increasing unsigned 64 bit integer.
     *
     * Note: Sometimes requests can arrive out of order or NTP can cause your clock to rewind,
     * resulting in nonce issues. If you encounter this issue, you can change the nonce window in
     * your account API settings page. The amount to set it to depends upon how you increment the
     * nonce. Depending on your connectivity, a setting that would accommodate 3-15 seconds of
     * network issues is suggested.
     */
    private ExchangeHttpResponse sendAuthenticatedRequestToExchange(
            String apiMethod, Map<String, String> params)
            throws ExchangeNetworkException, TradingApiException {

        if (!initializedMacAuthentication) {
            final String errorMsg = "MAC Message security layer has not been initialized.";
            LOG.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        try {
            if (params == null) {
                // create empty map for non param API calls, e.g. "trades"
                params = createRequestParamMap();
            }

            // The nonce is required by Kraken in every request.
            // It MUST be incremented each time and the nonce param MUST match the value used in
            // signature.
            nonce++;
            params.put("nonce", Long.toString(nonce));

            // Build the URL with query param args in it - yuk!
            final StringBuilder postData = new StringBuilder();
            for (final Map.Entry<String, String> param : params.entrySet()) {
                if (postData.length() > 0) {
                    postData.append("&");
                }
                postData.append(param.getKey());
                postData.append("=");
                postData.append(URLEncoder.encode(param.getValue(), StandardCharsets.UTF_8));
            }

            // And now the tricky part... ;-o
            final byte[] pathInBytes =
                    ("/" + CRYPTOCOM_API_VERSION + CRYPTOCOM_PRIVATE_PATH + apiMethod)
                            .getBytes(StandardCharsets.UTF_8);
            final String noncePrependedToPostData = Long.toString(nonce) + postData;

            // Create sha256 hash of nonce and post data:
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(noncePrependedToPostData.getBytes(StandardCharsets.UTF_8));
            final byte[] messageHash = md.digest();

            // Create hmac_sha512 digest of path and previous sha256 hash
            mac.reset(); // force reset
            mac.update(pathInBytes);
            mac.update(messageHash);

            // Signature in Base64
            final String signature = Base64.getEncoder().encodeToString(mac.doFinal());

            // Request headers required by Exchange
            final Map<String, String> requestHeaders = createHeaderParamMap();
            requestHeaders.put("Content-Type", "application/x-www-form-urlencoded");
            requestHeaders.put("API-Key", key);
            requestHeaders.put("API-Sign", signature);

            final URL url = new URL(AUTHENTICATED_API_URL + apiMethod);
            return makeNetworkRequest(url, "POST", postData.toString(), requestHeaders);

        } catch (MalformedURLException | NoSuchAlgorithmException e) {
            final String errorMsg = UNEXPECTED_IO_ERROR_MSG;
            LOG.error(errorMsg, e);
            throw new TradingApiException(errorMsg, e);
        }
    }

    /*
     * Initialises the secure messaging layer.
     * Sets up the MAC to safeguard the data we send to the exchange.
     * Used to encrypt the hash of the entire message with the private key to ensure message
     * integrity. We fail hard n fast if any of this stuff blows.
     */
    private void initSecureMessageLayer() {
        try {
            // Kraken secret key is in Base64, so we need to decode it first
            final byte[] base64DecodedSecret = Base64.getDecoder().decode(secret);

            final SecretKeySpec keyspec = new SecretKeySpec(base64DecodedSecret, "HmacSHA512");
            mac = Mac.getInstance("HmacSHA512");
            mac.init(keyspec);
            initializedMacAuthentication = true;
        } catch (NoSuchAlgorithmException e) {
            final String errorMsg = "Failed to setup MAC security. HINT: Is HmacSHA512 installed?";
            LOG.error(errorMsg, e);
            throw new IllegalStateException(errorMsg, e);
        } catch (InvalidKeyException e) {
            final String errorMsg = "Failed to setup MAC security. Secret key seems invalid!";
            LOG.error(errorMsg, e);
            throw new IllegalArgumentException(errorMsg, e);
        }
    }

    // --------------------------------------------------------------------------
    //  Config methods
    // --------------------------------------------------------------------------

    private void setAuthenticationConfig(ExchangeConfig exchangeConfig) {
        final AuthenticationConfig authenticationConfig = getAuthenticationConfig(exchangeConfig);
        key = getAuthenticationConfigItem(authenticationConfig, KEY_PROPERTY_NAME);
        secret = getAuthenticationConfigItem(authenticationConfig, SECRET_PROPERTY_NAME);
    }

    private void setOtherConfig(ExchangeConfig exchangeConfig) {
        final OtherConfig otherConfig = getOtherConfig(exchangeConfig);

        final String buyFeeInConfig = getOtherConfigItem(otherConfig, BUY_FEE_PROPERTY_NAME);
        buyFeePercentage =
                new BigDecimal(buyFeeInConfig).divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
        LOG.info(() -> "Buy fee % in BigDecimal format: " + buyFeePercentage);

        final String sellFeeInConfig = getOtherConfigItem(otherConfig, SELL_FEE_PROPERTY_NAME);
        sellFeePercentage =
                new BigDecimal(sellFeeInConfig).divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
        LOG.info(() -> "Sell fee % in BigDecimal format: " + sellFeePercentage);

        final String keepAliveDuringMaintenanceConfig =
                getOtherConfigItem(otherConfig, KEEP_ALIVE_DURING_MAINTENANCE_PROPERTY_NAME);
        if (!keepAliveDuringMaintenanceConfig.isEmpty()) {
            keepAliveDuringMaintenance = Boolean.valueOf(keepAliveDuringMaintenanceConfig);
            LOG.info(() -> "Keep Alive During Maintenance: " + keepAliveDuringMaintenance);
        } else {
            LOG.info(() -> KEEP_ALIVE_DURING_MAINTENANCE_PROPERTY_NAME + " is not set in exchange.yaml");
        }
    }


    // --------------------------------------------------------------------------
    //  Util methods
    // --------------------------------------------------------------------------

    private List<OpenOrder> adaptCryptoComOpenOrders(CryptoComExchangeAdapter.CryptoComResponse krakenResponse, String marketId)
            throws TradingApiException {
        final List<OpenOrder> openOrders = new ArrayList<>();

        // Assume we'll always get something here if errors array is empty; else blow fast wih NPE
        final CryptoComExchangeAdapter.CryptoComOpenOrderResult krakenOpenOrderResult =
                (CryptoComExchangeAdapter.CryptoComOpenOrderResult) krakenResponse.result;

        final Map<String, CryptoComExchangeAdapter.CryptoComOpenOrder> krakenOpenOrders = krakenOpenOrderResult.open;
        if (krakenOpenOrders != null) {
            for (final Map.Entry<String, CryptoComExchangeAdapter.CryptoComOpenOrder> openOrder : krakenOpenOrders.entrySet()) {

                OrderType orderType;
                final CryptoComExchangeAdapter.CryptoComOpenOrder krakenOpenOrder = openOrder.getValue();
                final CryptoComExchangeAdapter.CryptoComOpenOrderDescription krakenOpenOrderDescription = krakenOpenOrder.descr;

                if (!marketId.equalsIgnoreCase(krakenOpenOrderDescription.pair)) {
                    continue;
                }

                switch (krakenOpenOrderDescription.type) {
                    case "buy":
                        orderType = OrderType.BUY;
                        break;
                    case "sell":
                        orderType = OrderType.SELL;
                        break;
                    default:
                        throw new TradingApiException(
                                "Unrecognised order type received in getYourOpenOrders(). Value: "
                                        + openOrder.getValue().descr.ordertype);
                }

                final OpenOrder order =
                        new OpenOrderImpl(
                                openOrder.getKey(),
                                new Date((long) krakenOpenOrder.opentm), // opentm == creationDate
                                marketId,
                                orderType,
                                krakenOpenOrderDescription.price,
                                // vol_exec == amount of order that has been executed
                                (krakenOpenOrder.vol.subtract(krakenOpenOrder.volExec)),
                                krakenOpenOrder.vol, // vol == orig order amount
                                // krakenOpenOrder.cost, // cost == total value of order in API docs, but it's
                                // always 0 :-(
                                krakenOpenOrderDescription.price.multiply(krakenOpenOrder.vol));

                openOrders.add(order);
            }
        }
        return openOrders;
    }

    private MarketOrderBookImpl adaptKrakenOrderBook(CryptoComExchangeAdapter.CryptoComResponse cryptoComResponse, String marketId)
            throws TradingApiException {

        // Assume we'll always get something here if errors array is empty; else blow fast wih NPE
        final CryptoComExchangeAdapter.CryptoComMarketOrderBookResult krakenOrderBookResult =
                (CryptoComExchangeAdapter.CryptoComMarketOrderBookResult) cryptoComResponse.result;
        final Optional<CryptoComExchangeAdapter.CryptoComOrderBook> first = krakenOrderBookResult.values().stream().findFirst();
        if (first.isPresent()) {
            final CryptoComExchangeAdapter.CryptoComOrderBook cryptoComOrderBook = first.get();

            final List<MarketOrder> buyOrders = new ArrayList<>();
            for (CryptoComExchangeAdapter.CryptoComMarketOrder cryptoComBuyOrder : cryptoComOrderBook.bids) {
                final MarketOrder buyOrder =
                        new MarketOrderImpl(
                                OrderType.BUY,
                                cryptoComBuyOrder.get(0),
                                cryptoComBuyOrder.get(1),
                                cryptoComBuyOrder.get(0).multiply(cryptoComBuyOrder.get(1)));
                buyOrders.add(buyOrder);
            }

            final List<MarketOrder> sellOrders = new ArrayList<>();
            for (CryptoComExchangeAdapter.CryptoComMarketOrder cryptoComSellOrder : cryptoComOrderBook.asks) {
                final MarketOrder sellOrder =
                        new MarketOrderImpl(
                                OrderType.SELL,
                                cryptoComSellOrder.get(0),
                                cryptoComSellOrder.get(1),
                                cryptoComSellOrder.get(0).multiply(cryptoComSellOrder.get(1)));
                sellOrders.add(sellOrder);
            }
            return new MarketOrderBookImpl(marketId, sellOrders, buyOrders);
        } else {
            final String errorMsg = FAILED_TO_GET_MARKET_ORDERS + cryptoComResponse;
            LOG.error(errorMsg);
            throw new TradingApiException(errorMsg);
        }
    }

    private boolean adaptKrakenCancelOrderResult(CryptoComExchangeAdapter.CryptoComResponse cryptoComResponse) {
        // Assume we'll always get something here if errors array is empty; else blow fast wih NPE
        final CryptoComExchangeAdapter.CryptoComCancelOrderResult cryptoComCancelOrderResult =
                (CryptoComExchangeAdapter.CryptoComCancelOrderResult) cryptoComResponse.result;
        if (cryptoComCancelOrderResult != null) {
            if (cryptoComCancelOrderResult.count > 0) {
                return true;
            } else {
                final String errorMsg = FAILED_TO_CANCEL_ORDER + cryptoComResponse;
                LOG.error(errorMsg);
                return false;
            }
        } else {
            final String errorMsg = FAILED_TO_CANCEL_ORDER + cryptoComResponse;
            LOG.error(errorMsg);
            return false;
        }
    }

    private BalanceInfoImpl adaptCryptoComBalanceInfo(ExchangeHttpResponse response, Type resultType)
            throws ExchangeNetworkException, TradingApiException {
        final CryptoComExchangeAdapter.CryptoComResponse cryptoComResponse = gson.fromJson(response.getPayload(), resultType);
        if (cryptoComResponse != null) {
            final List errors = cryptoComResponse.error;
            if (errors == null || errors.isEmpty()) {
                // Assume we'll always get something here if errors array is empty; else blow fast wih NPE
                final CryptoComExchangeAdapter.CryptoComBalanceResult balanceResult = (CryptoComExchangeAdapter.CryptoComBalanceResult) cryptoComResponse.result;
                final Map<String, BigDecimal> balancesAvailable = new HashMap<>();
                final Set<Map.Entry<String, BigDecimal>> entries = balanceResult.entrySet();
                for (final Map.Entry<String, BigDecimal> entry : entries) {
                    balancesAvailable.put(entry.getKey(), entry.getValue());
                }

                // 2nd arg of BalanceInfo constructor for reserved/on-hold balances is not provided by
                // exchange.
                return new BalanceInfoImpl(balancesAvailable, new HashMap<>());

            } else {
                if (isExchangeUndergoingMaintenance(response) && keepAliveDuringMaintenance) {
                    LOG.warn(() -> UNDER_MAINTENANCE_WARNING_MESSAGE);
                    throw new ExchangeNetworkException(UNDER_MAINTENANCE_WARNING_MESSAGE);
                }

                final String errorMsg = FAILED_TO_GET_BALANCE + response;
                LOG.error(errorMsg);
                throw new TradingApiException(errorMsg);
            }
        } else {
            final String errorMsg = FAILED_TO_GET_BALANCE + response;
            LOG.error(errorMsg);
            throw new TradingApiException(errorMsg);
        }
    }

    private void initGson() {
        final GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(CryptoComExchangeAdapter.CryptoComTickerResult.class, new CryptoComExchangeAdapter.CryptoComTickerResultDeserializer());
        gson = gsonBuilder.create();
    }

    private static boolean isExchangeUndergoingMaintenance(ExchangeHttpResponse response) {
        if (response != null) {
            final String payload = response.getPayload();
            return payload != null && payload.contains(EXCHANGE_UNDERGOING_MAINTENANCE_RESPONSE);
        }
        return false;
    }


    //-----------------------------
    // Hacks for UnitTesting
    //-----------------------------

    /*
     * Hack for unit-testing map params passed to transport layer.
     */
    private Map<String, String> createRequestParamMap() {
        return new HashMap<>();
    }

    /*
     * Hack for unit-testing header params passed to transport layer.
     */
    private Map<String, String> createHeaderParamMap() {
        return new HashMap<>();
    }

    /*
     * Hack for unit-testing transport layer.
     */
    private ExchangeHttpResponse makeNetworkRequest(
            URL url, String httpMethod, String postData, Map<String, String> requestHeaders)
            throws TradingApiException, ExchangeNetworkException {
        return super.sendNetworkRequest(url, httpMethod, postData, requestHeaders);
    }

}
