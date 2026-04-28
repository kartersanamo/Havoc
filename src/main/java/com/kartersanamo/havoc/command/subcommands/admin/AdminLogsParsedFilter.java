package com.kartersanamo.havoc.command.subcommands.admin;

final class AdminLogsParsedFilter {
    boolean ok;
    String errorKey;
    String user = "";
    String base = "";
    String type = "";
    Long from;
    Long to;
    int page = 1;
    String scope = "all";

    void fail(String key) {
        ok = false;
        errorKey = key;
    }
}
