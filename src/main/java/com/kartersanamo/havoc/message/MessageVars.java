package com.kartersanamo.havoc.message;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared placeholder keys + fluent builder for message templates.
 */
public final class MessageVars {

    public static final class Key {
        public static final String AMOUNT = "amount";
        public static final String BALANCE = "balance";
        public static final String CLAIMS = "claims";
        public static final String COUNT = "count";
        public static final String DIFFICULTY = "difficulty";
        public static final String FILE = "file";
        public static final String ID = "id";
        public static final String PAGE = "page";
        public static final String PAGES = "pages";
        public static final String PLAYER = "player";
        public static final String SCOPE = "scope";
        public static final String STATE = "state";
        public static final String WORLD = "world";
        public static final String X = "x";
        public static final String Y = "y";
        public static final String Z = "z";

        private Key() {
        }
    }

    private final HashMap<String, String> vars = new HashMap<String, String>();

    private MessageVars() {
    }

    public static MessageVars create() {
        return new MessageVars();
    }

    public static Map<String, String> one(String key, String value) {
        return create().put(key, value).build();
    }

    public MessageVars put(String key, String value) {
        vars.put(key, value == null ? "" : value);
        return this;
    }

    public MessageVars put(String key, int value) {
        vars.put(key, String.valueOf(value));
        return this;
    }

    public MessageVars put(String key, long value) {
        vars.put(key, String.valueOf(value));
        return this;
    }

    public Map<String, String> build() {
        return Collections.unmodifiableMap(new HashMap<String, String>(vars));
    }
}
