package com.kartersanamo.havoc.command.subcommands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CommandUtil {

    private CommandUtil() {
    }

    public static List<String> partial(List<String> opts, String prefix) {
        List<String> out = new ArrayList<String>();
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        for (String o : opts) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }

    public static Map<String, String> one(String key, String value) {
        Map<String, String> out = new HashMap<String, String>();
        out.put(key, value);
        return out;
    }

    public static boolean isInt(String raw) {
        try {
            Integer.parseInt(raw);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
