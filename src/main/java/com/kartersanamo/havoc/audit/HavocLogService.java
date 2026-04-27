package com.kartersanamo.havoc.audit;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final File archiveDir;
    private final List<Entry> entries = new ArrayList<Entry>();
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private int maxLogLines = 100000;
    private int maxLogDays = 30;
    private boolean archiveOnRotate = true;

    public HavocLogService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "havoc-logs.log");
        this.archiveDir = new File(plugin.getDataFolder(), "log-archive");
    }

    public synchronized void configureRetention(int maxLogLines, int maxLogDays, boolean archiveOnRotate) {
        this.maxLogLines = Math.max(100, maxLogLines);
        this.maxLogDays = Math.max(1, maxLogDays);
        this.archiveOnRotate = archiveOnRotate;
        applyRetentionAndPersist(false);
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
        applyRetentionAndPersist(false);
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
        if (!applyRetentionAndPersist(true)) {
            appendLineAsync(serialize(e));
        }
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

    public synchronized List<Entry> queryByTypeNewestFirst(String type) {
        String t = safe(type).toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<Entry>();
        for (Entry e : entries) {
            if (!e.type.isEmpty() && e.type.toLowerCase(Locale.ROOT).contains(t)) {
                out.add(e);
            }
        }
        sortNewestFirst(out);
        return out;
    }

    public synchronized List<Entry> queryByDateRangeNewestFirst(long fromEpochMsInclusive, long toEpochMsInclusive) {
        List<Entry> out = new ArrayList<Entry>();
        for (Entry e : entries) {
            if (e.epochMs >= fromEpochMsInclusive && e.epochMs <= toEpochMsInclusive) {
                out.add(e);
            }
        }
        sortNewestFirst(out);
        return out;
    }

    public synchronized List<Entry> queryFilteredNewestFirst(String user, String base, String type, Long fromEpochMsInclusive, Long toEpochMsInclusive) {
        String u = safe(user).toLowerCase(Locale.ROOT);
        String b = safe(base).toLowerCase(Locale.ROOT);
        String t = safe(type).toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<Entry>();
        for (Entry e : entries) {
            if (!u.isEmpty() && (e.user.isEmpty() || !e.user.toLowerCase(Locale.ROOT).contains(u))) {
                continue;
            }
            if (!b.isEmpty() && (e.baseId.isEmpty() || !e.baseId.toLowerCase(Locale.ROOT).contains(b))) {
                continue;
            }
            if (!t.isEmpty() && (e.type.isEmpty() || !e.type.toLowerCase(Locale.ROOT).contains(t))) {
                continue;
            }
            if (fromEpochMsInclusive != null && e.epochMs < fromEpochMsInclusive.longValue()) {
                continue;
            }
            if (toEpochMsInclusive != null && e.epochMs > toEpochMsInclusive.longValue()) {
                continue;
            }
            out.add(e);
        }
        sortNewestFirst(out);
        return out;
    }

    public synchronized int exportToFile(File outFile, List<Entry> data) {
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(outFile, false));
            for (Entry e : data) {
                bw.write(serialize(e));
                bw.newLine();
            }
            return data.size();
        } catch (IOException e) {
            plugin.getLogger().warning("Could not export havoc logs: " + e.getMessage());
            return -1;
        } finally {
            if (bw != null) {
                try {
                    bw.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static Long parseDateStartEpochMs(String yyyyMmDd) {
        return parseDateEpoch(yyyyMmDd, true);
    }

    public static Long parseDateEndEpochMs(String yyyyMmDd) {
        return parseDateEpoch(yyyyMmDd, false);
    }

    /**
     * Supported: today, yesterday, last7d (server local timezone).
     */
    public static long[] parseRelativeRange(String raw) {
        if (raw == null) {
            return null;
        }
        String k = raw.trim().toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        long day = 24L * 60L * 60L * 1000L;
        if ("last7d".equals(k)) {
            return new long[]{now - 7L * day, now};
        }
        Long startToday = parseDateStartEpochMs(formatYmd(now));
        if (startToday == null) {
            return null;
        }
        if ("today".equals(k)) {
            return new long[]{startToday.longValue(), now};
        }
        if ("yesterday".equals(k)) {
            long from = startToday.longValue() - day;
            long to = startToday.longValue() - 1L;
            return new long[]{from, to};
        }
        return null;
    }

    private void sortNewestFirst(List<Entry> out) {
        Collections.sort(out, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return Long.compare(b.epochMs, a.epochMs);
            }
        });
    }

    private boolean applyRetentionAndPersist(boolean maybeArchive) {
        long now = System.currentTimeMillis();
        long minEpoch = now - (maxLogDays * 24L * 60L * 60L * 1000L);
        int before = entries.size();
        List<Entry> kept = new ArrayList<Entry>(entries.size());
        for (Entry e : entries) {
            if (e.epochMs >= minEpoch) {
                kept.add(e);
            }
        }
        if (kept.size() > maxLogLines) {
            kept = new ArrayList<Entry>(kept.subList(kept.size() - maxLogLines, kept.size()));
        }
        boolean trimmed = kept.size() != before;
        if (trimmed) {
            entries.clear();
            entries.addAll(kept);
            rewriteAllAsync(maybeArchive && archiveOnRotate);
        }
        return trimmed;
    }

    private void appendLineAsync(final String line) {
        ioExecutor.submit(new Runnable() {
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

    private void rewriteAllAsync(final boolean archiveCurrentFile) {
        final List<Entry> snapshot = new ArrayList<Entry>(entries);
        ioExecutor.submit(new Runnable() {
            @Override
            public void run() {
                if (archiveCurrentFile) {
                    archiveCurrentLogFile();
                }
                BufferedWriter bw = null;
                try {
                    bw = new BufferedWriter(new FileWriter(file, false));
                    for (Entry e : snapshot) {
                        bw.write(serialize(e));
                        bw.newLine();
                    }
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not rewrite havoc log file: " + e.getMessage());
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

    private void archiveCurrentLogFile() {
        if (!file.exists()) {
            return;
        }
        if (!archiveDir.exists() && !archiveDir.mkdirs()) {
            plugin.getLogger().warning("Could not create log archive directory: " + archiveDir.getAbsolutePath());
            return;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File target = new File(archiveDir, "havoc-logs-" + stamp + ".log");
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(file);
            out = new FileOutputStream(target);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not archive havoc log file: " + e.getMessage());
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public void shutdown() {
        ioExecutor.shutdown();
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

    private static Long parseDateEpoch(String raw, boolean start) {
        if (raw == null) {
            return null;
        }
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setLenient(false);
        try {
            Date d = f.parse(raw);
            if (d == null) {
                return null;
            }
            long base = d.getTime();
            if (start) {
                return base;
            }
            return base + (24L * 60L * 60L * 1000L) - 1L;
        } catch (ParseException e) {
            return null;
        }
    }

    private static String formatYmd(long epochMs) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getDefault());
        return f.format(new Date(epochMs));
    }
}
