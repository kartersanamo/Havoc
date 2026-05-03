package com.kartersanamo.havoc.message;

import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class MessageVarsTest {

    @Test
    public void oneBuildsSingleMap() {
        Map<String, String> map = MessageVars.one(MessageKeys.PLAYER, "sanamo");
        Assert.assertEquals(1, map.size());
        Assert.assertEquals("sanamo", map.get(MessageKeys.PLAYER));
    }

    @Test
    public void builderNormalizesAndStringifiesValues() {
        Map<String, String> map = MessageVars.create()
                .put(MessageKeys.AMOUNT, 5)
                .put(MessageKeys.BALANCE, 99L)
                .put(MessageKeys.SCOPE, null)
                .build();
        Assert.assertEquals("5", map.get(MessageKeys.AMOUNT));
        Assert.assertEquals("99", map.get(MessageKeys.BALANCE));
        Assert.assertEquals("", map.get(MessageKeys.SCOPE));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void builtMapIsImmutable() {
        Map<String, String> map = MessageVars.create().put("x", "y").build();
        map.put("a", "b");
    }
}
