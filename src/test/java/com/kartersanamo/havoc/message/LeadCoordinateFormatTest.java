package com.kartersanamo.havoc.message;

import org.bukkit.ChatColor;
import org.junit.Assert;
import org.junit.Test;

public class LeadCoordinateFormatTest {

    @Test
    public void obfuscatesFirstDigitOfPositiveCoordinate() {
        String formatted = LeadCoordinateFormat.obfuscateFirstDigit(1234);
        Assert.assertTrue(formatted.contains(String.valueOf(ChatColor.MAGIC)));
        Assert.assertTrue(formatted.endsWith("234"));
    }

    @Test
    public void obfuscatesFirstDigitOfNegativeCoordinate() {
        String formatted = LeadCoordinateFormat.obfuscateFirstDigit(-5678);
        Assert.assertTrue(formatted.startsWith("-" + ChatColor.MAGIC));
        Assert.assertTrue(formatted.endsWith("678"));
    }

    @Test
    public void obfuscatesSingleDigitCoordinate() {
        String formatted = LeadCoordinateFormat.obfuscateFirstDigit(9);
        Assert.assertEquals(String.valueOf(ChatColor.MAGIC) + "9" + ChatColor.GRAY, formatted);
    }
}
