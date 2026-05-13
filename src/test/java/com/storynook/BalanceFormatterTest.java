package com.storynook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class BalanceFormatterTest {

    @Test
    public void zeroFormatsAsDollarZero() {
        assertEquals("$0", BalanceFormatter.format(0.0));
    }

    @Test
    public void smallIntegerFormatsWithNoSeparator() {
        assertEquals("$1", BalanceFormatter.format(1.0));
    }

    @Test
    public void thousandsFormatWithCommaSeparator() {
        assertEquals("$1,234", BalanceFormatter.format(1234.0));
    }

    @Test
    public void justUnderHundredKKeepsCommaFormat() {
        assertEquals("$99,999", BalanceFormatter.format(99999.0));
    }

    @Test
    public void fractionalCentsTruncateToInteger() {
        assertEquals("$99", BalanceFormatter.format(99.99));
    }

    @Test
    public void hundredKFormatsWithKSuffix() {
        assertEquals("$100K", BalanceFormatter.format(100_000.0));
    }

    @Test
    public void justUnderOneMillionFormatsAsK() {
        assertEquals("$999K", BalanceFormatter.format(999_999.0));
    }

    @Test
    public void oneMillionFormatsAsOnePointZeroM() {
        assertEquals("$1.0M", BalanceFormatter.format(1_000_000.0));
    }

    @Test
    public void onePointFiveMillionFormatsAsOnePointFiveM() {
        assertEquals("$1.5M", BalanceFormatter.format(1_500_000.0));
    }

    @Test
    public void mTierTruncatesNotRounds() {
        // 1,299,999 truncates to $1.2M, never rounds up to $1.3M
        assertEquals("$1.2M", BalanceFormatter.format(1_299_999.0));
    }

    @Test
    public void tenMillionDropsDecimal() {
        assertEquals("$10M", BalanceFormatter.format(10_000_000.0));
    }

    @Test
    public void justUnderOneBillionFormatsAsM() {
        assertEquals("$999M", BalanceFormatter.format(999_000_000.0));
    }

    @Test
    public void oneBillionFormatsAsOnePointZeroB() {
        assertEquals("$1.0B", BalanceFormatter.format(1_000_000_000.0));
    }

    @Test
    public void onePointTwoBillionFormatsAsOnePointTwoB() {
        assertEquals("$1.2B", BalanceFormatter.format(1_200_000_000.0));
    }

    @Test
    public void tenBillionDropsDecimal() {
        assertEquals("$10B", BalanceFormatter.format(10_000_000_000.0));
    }

    @Test
    public void justUnderTrillionFormatsAsB() {
        assertEquals("$999B", BalanceFormatter.format(999_000_000_000.0));
    }

    @Test
    public void oneTrillionFormatsAsOnePointZeroT() {
        assertEquals("$1.0T", BalanceFormatter.format(1_000_000_000_000.0));
    }

    @Test
    public void tenTrillionDropsDecimal() {
        assertEquals("$10T", BalanceFormatter.format(10_000_000_000_000.0));
    }

    @Test
    public void aboveTenTrillionStaysInTTier() {
        assertEquals("$1234T", BalanceFormatter.format(1_234_000_000_000_000.0));
    }

    @Test
    public void negativeSmallBalancePrependsMinusBeforeDollar() {
        assertEquals("-$1,234", BalanceFormatter.format(-1234.0));
    }

    @Test
    public void negativeMillionPrependsMinusBeforeDollar() {
        assertEquals("-$1.5M", BalanceFormatter.format(-1_500_000.0));
    }

    @Test
    public void negativeBillionUsesBTier() {
        assertEquals("-$1.2B", BalanceFormatter.format(-1_200_000_000.0));
    }

    @Test
    public void negativeNearZeroSuppressesSign() {
        // -0.5 truncates to $0 — don't display "-$0"
        assertEquals("$0", BalanceFormatter.format(-0.5));
    }
}
