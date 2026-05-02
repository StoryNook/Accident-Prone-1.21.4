package com.storynook;

import java.util.Locale;

public final class BalanceFormatter {
    private BalanceFormatter() {}

    public static String format(double amount) {
        boolean negative = amount < 0;
        double abs = Math.abs(amount);
        long whole = (long) abs;
        String body;

        if (whole < 100_000L) {
            body = String.format(Locale.ROOT, "%,d", whole);
        } else if (whole < 1_000_000L) {
            body = (whole / 1_000L) + "K";
        } else if (whole < 10_000_000L) {
            long tenths = whole / 100_000L;
            body = String.format(Locale.ROOT, "%d.%dM", tenths / 10, tenths % 10);
        } else if (whole < 1_000_000_000L) {
            body = (whole / 1_000_000L) + "M";
        } else if (whole < 10_000_000_000L) {
            long tenths = whole / 100_000_000L;
            body = String.format(Locale.ROOT, "%d.%dB", tenths / 10, tenths % 10);
        } else if (whole < 1_000_000_000_000L) {
            body = (whole / 1_000_000_000L) + "B";
        } else if (whole < 10_000_000_000_000L) {
            long tenths = whole / 100_000_000_000L;
            body = String.format(Locale.ROOT, "%d.%dT", tenths / 10, tenths % 10);
        } else {
            body = (whole / 1_000_000_000_000L) + "T";
        }

        if (negative && whole != 0) {
            return "-$" + body;
        }
        return "$" + body;
    }
}
