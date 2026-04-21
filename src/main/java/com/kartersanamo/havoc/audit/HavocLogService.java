package com.kartersanamo.havoc.audit;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class HavocLogService {

    public static final class Entry {
        public final long epochMs;
        public final String type;
        public final String user;
        public final String baseId;
        public final String world;
        public final int x;
        public final int y;
        public final int z;
        public final String message;

        public Entry(long epochMs, String type, String user, String baseId, String world, int x, int y, int z, String message) {
            this.epochMs = epochMs;
            this.type = type;
            this.user = user;
            this.baseId = baseId;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.message = message;
        }
    }

    private final JavaPlugin plugin;
    private final File file;
    private final List<Entry> entries = new ArrayList<Entry>();
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    public HavocLogService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "havoc-logs.log");
    }

    public synchronized void load() {
        entries.clear();
        if (!file.exists()) {
            return;
        }
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                Entry e = parse(line);
                if (e != null) {
                    entries.add(e);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not load havoc-logs.log: " + e.getMessage());
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public synchronized void log(String type, String user, String baseId, Location loc, String message) {
        String world = loc != null && loc.getWorld() != null ? loc.getWorld().getName() : "";
        int x = loc != null ? loc.getBlockX() : 0;
        int y = loc != null ? loc.getBlockY() : 0;
        int z = loc != null ? loc.getBlockZ() : 0;
        Entry e = new Entry(System.currentTimeMillis(),
                safe(type),
                safe(user),
                safe(baseId),
                safe(world),
                x, y, z,
                safe(message));
        entries.add(e);
        appendLine(serialize(e));
    }

    public synchronized List<Entry> queryAllNewestFirst() {
        List<Entry> out = new ArrayList<Entry>(entries);
        sortNewestFirst(out);
        return out;
    }

    public synchronized List<Entry> queryByUserNewestFirst(String user) {
        String u = safe(user).toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<Entry>();
        for (Entry e : entries) {
            if (!e.user.isEmpty() && e.user.toLowerCase(Locale.ROOT).contains(u)) {
                out.add(e);
            }
        }
        sortNewestFirst(out);
        return out;
    }

    public synchronized List<Entry> queryByBaseNewestFirst(String base) {
        String b = safe(base).toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<Entry>();
        for (Entry e : entries) {
            if (!e.baseId.isEmpty() && e.baseId.toLowerCase(Locale.ROOT).contains(b)) {
                out.add(e);
            }
        }
        sortNewestFirst(out);
        return out;
    }

    public String formatForChat(Entry e) {
        String t = dateFmt.format(new Date(e.epochMs));
        String loc = e.world.isEmpty() ? "-" : e.world + "@" + e.x + "," + e.y + "," + e.z;
        return "§8[" + t + "] §b" + e.type
                + " §7user=§f" + (e.user.isEmpty() ? "-" : e.user)
                + " §7base=§f" + (e.baseId.isEmpty() ? "-" : e.baseId)
                + " §7loc=§f" + loc
                + " §7- " + e.message;
    }

    private void sortNewestFirst(List<Entry> out) {
        Collections.sort(out, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return Long.compare(b.epochMs, a.epochMs);
            }
        });
    }

    private void appendLine(final String line) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                BufferedWriter bw = null;
                try {
                    bw = new BufferedWriter(new FileWriter(file, true));
                    bw.write(line);
                    bw.newLine();
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not append havoc log: " + e.getMessage());
                } finally {
                    if (bw != null) {
                        try {
                            bw.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
        });
    }

    private String serialize(Entry e) {
        return e.epochMs + "|" + esc(e.type) + "|" + esc(e.user) + "|" + esc(e.baseId) + "|"
                + esc(e.world) + "|" + e.x + "|" + e.y + "|" + e.z + "|" + esc(e.message);
    }

    private Entry parse(String line) {
        String[] p = split(line, 9);
        if (p.length < 9) {
            return null;
        }
        try {
            long ts = Long.parseLong(p[0]);
            return new Entry(ts, unesc(p[1]), unesc(p[2]), unesc(p[3]), unesc(p[4]),
                    Integer.parseInt(p[5]), Integer.parseInt(p[6]), Integer.parseInt(p[7]), unesc(p[8]));
        } catch (Exception e) {
            return null;
        }
    }

    private static String[] split(String raw, int max) {
        return raw.split("\\|", max);
    }

    private static String esc(String s) {
        return safe(s).replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n").replace("\r", "");
    }

    private static String unesc(String s) {
        String out = safe(s);
        out = out.replace("\\n", "\n");
        out = out.replace("\\p", "|");
        out = out.replace("\\\\", "\\");
        return out;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
