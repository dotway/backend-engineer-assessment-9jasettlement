package com.wallet.constants;

/**
 * Application-wide constants for the wallet service.
 */
public final class WalletConstants {

    private WalletConstants() {
    }
    public static final String DEFAULT_CURRENCY = "NGN";

    public static final int CURRENCY_CODE_LENGTH = 3;

    public static final Long DEFAULT_BALANCE = 0L;

    public static final int MINOR_UNITS_DIVISOR = 100;

    public static final String TRANSFER_SENDER_KEY_PREFIX = "transfer:";

    public static final String TRANSFER_SENDER_KEY_SUFFIX = ":out";

    public static final String TRANSFER_RECEIVER_KEY_SUFFIX = ":in";

    public static final int MAX_WALLET_NAME_LENGTH = 100;

    public static final String CURRENCY_PATTERN = "^[A-Z]{3}$";
}

