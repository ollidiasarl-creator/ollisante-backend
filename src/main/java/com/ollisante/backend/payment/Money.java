package com.ollisante.backend.payment;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/** Montants stockés en unité minimale : franc entier pour XOF/XAF/GNF/CDF…, centimes pour USD/EUR/CHF. */
public final class Money {

    private static final Set<String> ZERO_DECIMAL = Set.of("XOF", "XAF", "GNF", "CDF", "RWF", "UGX");

    private Money() {
    }

    public static boolean zeroDecimal(String currency) {
        return ZERO_DECIMAL.contains(currency);
    }

    /** Montant pour l'API PawaPay : "500", "10" ou "5.50". */
    public static String toApi(long minor, String currency) {
        if (zeroDecimal(currency)) return Long.toString(minor);
        return BigDecimal.valueOf(minor).movePointLeft(2).stripTrailingZeros().toPlainString();
    }

    /** Pourcentage d'un montant, arrondi à l'unité inférieure (jamais plus que le pourcentage dû). */
    public static long percent(long minor, int percent) {
        return Math.floorDiv(minor * percent, 100);
    }

    /** Montant lisible pour un e-mail : « 10 000 XOF », « 10,00 EUR ». */
    public static String format(long minor, String currency) {
        String amount = zeroDecimal(currency)
                ? String.format(Locale.FRANCE, "%,d", minor)
                : String.format(Locale.FRANCE, "%,.2f", minor / 100.0);
        return (amount + " " + currency).replace('\u202f', ' ').replace('\u00a0', ' ');
    }

    /** Code pays ISO alpha-3 (SEN) à partir de l'alpha-2 (SN), utilisé par PawaPay. */
    public static String iso3(String iso2) {
        return new Locale("", iso2).getISO3Country();
    }
}
