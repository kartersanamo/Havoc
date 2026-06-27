package com.kartersanamo.havoc.audit;

import com.kartersanamo.havoc.storage.DatabaseSupport;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HavocLogService {

    private final JavaPlugin plugin;
    private final File file;
    private final File archiveDir;
    private final List<HavocLogEntry> entries = new ArrayList<HavocLogEntry>();
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private DatabaseSupport database;
    private int maxLogLines = 100000;
    private int maxLogDays = 30;
    private boolean archiveOnRotate = true;

    public HavocLogService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "havoc-logs.log");
        this.archiveDir = new File(plugin.getDataFolder(), "log-archive");
    }

    public synchronized void setDatabase(DatabaseSupport database) {
        this.database = database;
    }

    public synchronized void configureRetention(int maxLogLines, int maxLogDays, boolean archiveOnRotate) {
        this.maxLogLines = Math.max(100, maxLogLines);
        this.maxLogDays = Math.max(1, maxLogDays);
        this.archiveOnRotate = archiveOnRotate;
        applyRetentionAndPersist(false);
    }

    public synchronized void load() {
        entries.clear();
        if (isDatabaseActive()) {
            loadFromDatabase();
            applyRetentionAndPersist(false);
            return;
        }
        if (!file.exists()) {
            return;
        }
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                HavocLogEntry e = parse(line);
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
        HavocLogEntry e = new HavocLogEntry(System.currentTimeMillis(),
                safe(type),
                safe(user),
                safe(baseId),
                safe(world),
                x, y, z,
                safe(message));
        entries.add(e);
        if (!applyRetentionAndPersist(true)) {
            if (isDatabaseActive()) {
                appendDbAsync(e);
            } else {
                appendLineAsync(serialize(e));
            }
        }
    }

    public synchronized List<HavocLogEntry> queryAllNewestFirst() {
        List<HavocLogEntry> out = new ArrayList<HavocLogEntry>(entries);
        sortNewestFirst(out);
        return out;
    }

    public String formatForChat(HavocLogEntry e) {
        String t = dateFmt.format(new Date(e.epochMs));
        String loc = e.world.isEmpty() ? "-" : e.world + "@" + e.x + "," + e.y + "," + e.z;
        return "§8[" + t + "] §b" + e.type
                + " §7user=§f" + (e.user.isEmpty() ? "-" : e.user)
                + " §7base=§f" + (e.baseId.isEmpty() ? "-" : e.baseId)
                + " §7loc=§f" + loc
                + " §7- " + e.message;
    }

    private void sortNewestFirst(List<HavocLogEntry> out) {
        Collections.sort(out, new Comparator<HavocLogEntry>() {
            @Override
            public int compare(HavocLogEntry a, HavocLogEntry b) {
                return Long.compare(b.epochMs, a.epochMs);
            }
        });
    }

    private boolean applyRetentionAndPersist(boolean maybeArchive) {
        long now = System.currentTimeMillis();
        long minEpoch = now - (maxLogDays * 24L * 60L * 60L * 1000L);
        int before = entries.size();
        List<HavocLogEntry> kept = new ArrayList<HavocLogEntry>(entries.size());
        for (HavocLogEntry e : entries) {
            if (e.epochMs >= minEpoch) {
                kept.add(e);
            }
        }
        if (kept.size() > maxLogLines) {
            kept = new ArrayList<HavocLogEntry>(kept.subList(kept.size() - maxLogLines, kept.size()));
        }
        boolean trimmed = kept.size() != before;
        if (trimmed) {
            entries.clear();
            entries.addAll(kept);
            if (isDatabaseActive()) {
                rewriteAllDbAsync();
            } else {
                rewriteAllAsync(maybeArchive && archiveOnRotate);
            }
        }
        return trimmed;
    }

    private boolean isDatabaseActive() {
        return database != null && database.isEnabled();
    }

    private void loadFromDatabase() {
        try (Connection c = database.openConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT epoch_ms, type, user_name, base_id, world_name, x, y, z, message FROM havoc_logs ORDER BY id ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                entries.add(new HavocLogEntry(
                        rs.getLong(1),
                        safe(rs.getString(2)),
                        safe(rs.getString(3)),
                        safe(rs.getString(4)),
                        safe(rs.getString(5)),
                        rs.getInt(6),
                        rs.getInt(7),
                        rs.getInt(8),
                        safe(rs.getString(9))
                ));
            }
            if (entries.isEmpty() && file.exists()) {
                loadFromFileToEntries();
                if (!entries.isEmpty()) {
                    rewriteAllDbAsync();
                    plugin.getLogger().info("Imported havoc-logs.log into MySQL (" + entries.size() + " entries).");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load havoc logs from database: " + e.getMessage());
        }
    }

    private void loadFromFileToEntries() {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                HavocLogEntry e = parse(line);
                if (e != null) {
                    entries.add(e);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not load havoc-logs.log for DB import: " + e.getMessage());
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void appendDbAsync(final HavocLogEntry entry) {
        ioExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try (Connection c = database.openConnection();
                     PreparedStatement ps = c.prepareStatement(
                             "INSERT INTO havoc_logs (epoch_ms, type, user_name, base_id, world_name, x, y, z, message) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setLong(1, entry.epochMs);
                    ps.setString(2, safe(entry.type));
                    ps.setString(3, safe(entry.user));
                    ps.setString(4, safe(entry.baseId));
                    ps.setString(5, safe(entry.world));
                    ps.setInt(6, entry.x);
                    ps.setInt(7, entry.y);
                    ps.setInt(8, entry.z);
                    ps.setString(9, safe(entry.message));
                    ps.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().warning("Could not append havoc log to database: " + e.getMessage());
                }
            }
        });
    }

    private void rewriteAllDbAsync() {
        final List<HavocLogEntry> snapshot = new ArrayList<HavocLogEntry>(entries);
        ioExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try (Connection c = database.openConnection()) {
                    c.setAutoCommit(false);
                    try (PreparedStatement clear = c.prepareStatement("DELETE FROM havoc_logs")) {
                        clear.executeUpdate();
                    }
                    try (PreparedStatement insert = c.prepareStatement(
                            "INSERT INTO havoc_logs (epoch_ms, type, user_name, base_id, world_name, x, y, z, message) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                        for (HavocLogEntry e : snapshot) {
                            insert.setLong(1, e.epochMs);
                            insert.setString(2, safe(e.type));
                            insert.setString(3, safe(e.user));
                            insert.setString(4, safe(e.baseId));
                            insert.setString(5, safe(e.world));
                            insert.setInt(6, e.x);
                            insert.setInt(7, e.y);
                            insert.setInt(8, e.z);
                            insert.setString(9, safe(e.message));
                            insert.addBatch();
                        }
                        insert.executeBatch();
                    }
                    c.commit();
                } catch (SQLException e) {
                    plugin.getLogger().warning("Could not rewrite havoc logs in database: " + e.getMessage());
                }
            }
        });
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
        final List<HavocLogEntry> snapshot = new ArrayList<HavocLogEntry>(entries);
        ioExecutor.submit(new Runnable() {
            @Override
            public void run() {
                if (archiveCurrentFile) {
                    archiveCurrentLogFile();
                }
                BufferedWriter bw = null;
                try {
                    bw = new BufferedWriter(new FileWriter(file, false));
                    for (HavocLogEntry e : snapshot) {
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

    private String serialize(HavocLogEntry e) {
        return e.epochMs + "|" + esc(e.type) + "|" + esc(e.user) + "|" + esc(e.baseId) + "|"
                + esc(e.world) + "|" + e.x + "|" + e.y + "|" + e.z + "|" + esc(e.message);
    }

    private HavocLogEntry parse(String line) {
        String[] p = split(line, 9);
        if (p.length < 9) {
            return null;
        }
        try {
            long ts = Long.parseLong(p[0]);
            return new HavocLogEntry(ts, unesc(p[1]), unesc(p[2]), unesc(p[3]), unesc(p[4]),
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
