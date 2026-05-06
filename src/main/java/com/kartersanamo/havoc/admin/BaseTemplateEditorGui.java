package com.kartersanamo.havoc.admin;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.BaseDifficulty;
import com.kartersanamo.havoc.generator.BaseTemplateDefinition;
import com.kartersanamo.havoc.generator.BaseTemplateSaveCoordinator;
import com.kartersanamo.havoc.generator.BaseTemplateValidation;
import com.kartersanamo.havoc.generator.DefenseSection;
import com.kartersanamo.havoc.generator.DefenseType;
import com.kartersanamo.havoc.message.MessageKeys;
import com.kartersanamo.havoc.message.MessageVars;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class BaseTemplateEditorGui {

    private static final int SIZE = 54;
    private static final int SLOT_ADD_FLAT = 0;
    private static final int SLOT_ADD_REGEN = 1;
    private static final int SLOT_ADD_SAND = 2;
    private static final int SLOT_REMOVE = 3;
    private static final int SLOT_REP_MINUS = 4;
    private static final int SLOT_REP_PLUS = 5;
    private static final int SLOT_SIZE = 6;
    private static final int SLOT_SLABS = 7;
    private static final int SLOT_SAVE = 8;
    private static final int SLOT_BACK = 9;
    private static final int SLOT_PAGE_PREV = 10;
    private static final int SLOT_PAGE_NEXT = 11;
    private static final int SLOT_INS_BEFORE_FLAT = 12;
    private static final int SLOT_INS_BEFORE_REGEN = 13;
    private static final int SLOT_INS_BEFORE_SAND = 14;
    private static final int SLOT_MOVE_UP = 15;
    private static final int SLOT_MOVE_DOWN = 16;
    private static final int SLOT_SECTIONS_START = 18;
    private static final int SECTIONS_PER_PAGE = 9;

    private final Havoc plugin;

    public BaseTemplateEditorGui(Havoc plugin) {
        this.plugin = plugin;
    }

    public void openDifficultyList(Player player) {
        Inventory inv = Bukkit.createInventory(new BaseTemplateDifficultyListHolder(), 27,
                ChatColor.DARK_GREEN + "Havoc Base Templates");
        inv.setItem(11, createDifficultyItem(BaseDifficulty.EASY, Material.EMERALD_BLOCK, ChatColor.GREEN));
        inv.setItem(13, createDifficultyItem(BaseDifficulty.MEDIUM, Material.GOLD_BLOCK, ChatColor.GOLD));
        inv.setItem(15, createDifficultyItem(BaseDifficulty.HARD, Material.REDSTONE_BLOCK, ChatColor.RED));
        player.openInventory(inv);
    }

    public void openEditor(Player player, BaseDifficulty difficulty) {
        BaseTemplateDefinition draft = plugin.getBaseTemplateStore().load(difficulty).copy();
        openEditor(player, draft, 0, -1);
    }

    private void openEditor(Player player, BaseTemplateDefinition draft, int page, int selectedIndex) {
        int maxPage = Math.max(0, (draft.getSectionsMutable().size() + SECTIONS_PER_PAGE - 1) / SECTIONS_PER_PAGE - 1);
        if (page < 0) {
            page = 0;
        }
        if (page > maxPage) {
            page = maxPage;
        }
        Inventory inv = Bukkit.createInventory(new BaseTemplateEditorHolder(draft, page, selectedIndex), SIZE,
                ChatColor.DARK_AQUA + "Edit " + draft.getDifficulty().name());

        inv.setItem(SLOT_ADD_FLAT, icon(Material.OBSIDIAN, ChatColor.WHITE + "Add Flat wall",
                line("Append FLAT segment (or insert after selection)")));
        inv.setItem(SLOT_ADD_REGEN, icon(Material.LAVA_BUCKET, ChatColor.RED + "Add Regen wall",
                line("Append REGEN segment (or insert after selection)")));
        inv.setItem(SLOT_ADD_SAND, icon(Material.SAND, ChatColor.YELLOW + "Add Sand wall",
                line("Append SAND segment (or insert after selection)")));
        inv.setItem(SLOT_REMOVE, icon(Material.BARRIER, ChatColor.DARK_RED + "Remove selected",
                line("Removes highlighted section")));
        inv.setItem(SLOT_REP_MINUS, icon(Material.REDSTONE_TORCH_OFF, ChatColor.RED + "Repeats -1",
                line("Selected section")));
        inv.setItem(SLOT_REP_PLUS, icon(Material.REDSTONE_TORCH_ON, ChatColor.GREEN + "Repeats +1",
                line("Selected section")));
        inv.setItem(SLOT_SIZE, icon(Material.MAP, ChatColor.AQUA + "Footprint: " + draft.getSizeChunksOdd() + "x" + chunkLabel(draft.getSizeChunksOdd()),
                line("Click to cycle 1 -> 3 -> 5 chunks")));
        inv.setItem(SLOT_SLABS, icon(draft.isSlabFloorBetweenWalls() ? Material.STEP : Material.WOOD_STEP,
                ChatColor.GRAY + "Slab floor (Y=1): " + onOff(draft.isSlabFloorBetweenWalls()),
                line("Bottom slabs between walls to reduce sand falling")));
        inv.setItem(SLOT_SAVE, icon(Material.EMERALD, ChatColor.GREEN + "Save + export",
                line("Writes .schematic + activates for spawns")));

        inv.setItem(SLOT_BACK, icon(Material.ARROW, ChatColor.WHITE + "Back",
                Collections.singletonList(ChatColor.GRAY + "Difficulty list")));
        inv.setItem(SLOT_PAGE_PREV, icon(Material.PAPER, ChatColor.WHITE + "Prev page",
                Collections.singletonList(ChatColor.GRAY + "Page " + (page + 1))));
        inv.setItem(SLOT_PAGE_NEXT, icon(Material.PAPER, ChatColor.WHITE + "Next page",
                Collections.singletonList(ChatColor.GRAY + "Page " + (page + 1))));
        inv.setItem(SLOT_INS_BEFORE_FLAT, icon(Material.OBSIDIAN, ChatColor.GRAY + "Insert Flat BEFORE",
                line("Before selected row, or at start if none selected")));
        inv.setItem(SLOT_INS_BEFORE_REGEN, icon(Material.LAVA_BUCKET, ChatColor.GRAY + "Insert Regen BEFORE",
                line("Before selected row, or at start if none selected")));
        inv.setItem(SLOT_INS_BEFORE_SAND, icon(Material.SAND, ChatColor.GRAY + "Insert Sand BEFORE",
                line("Before selected row, or at start if none selected")));
        inv.setItem(SLOT_MOVE_UP, icon(Material.FEATHER, ChatColor.YELLOW + "Move selected UP",
                line("Swap with previous section")));
        inv.setItem(SLOT_MOVE_DOWN, icon(Material.FEATHER, ChatColor.YELLOW + "Move selected DOWN",
                line("Swap with next section")));

        List<DefenseSection> secs = draft.getSectionsMutable();
        int start = page * SECTIONS_PER_PAGE;
        for (int i = 0; i < SECTIONS_PER_PAGE; i++) {
            int idx = start + i;
            int slot = SLOT_SECTIONS_START + i;
            if (idx >= secs.size()) {
                inv.setItem(slot, icon(Material.STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "-",
                        Collections.singletonList(ChatColor.DARK_GRAY + "Empty")));
                continue;
            }
            DefenseSection s = secs.get(idx);
            boolean sel = idx == selectedIndex;
            Material mat = materialFor(s.getType());
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.WHITE + s.getType().name());
            lore.add(ChatColor.GRAY + "Repeats: " + ChatColor.YELLOW + s.getRepeats());
            lore.add(ChatColor.DARK_GRAY + "Click to select");
            if (sel) {
                lore.add(ChatColor.GOLD + "SELECTED");
            }
            ItemStack it = icon(mat, (sel ? ChatColor.GOLD + "" : ChatColor.WHITE + "") + s.getType().displayName() + " x" + s.getRepeats(), lore);
            inv.setItem(slot, it);
        }

        player.openInventory(inv);
    }

    public boolean isTemplateDifficultyList(Inventory inv) {
        return inv != null && inv.getHolder() instanceof BaseTemplateDifficultyListHolder;
    }

    public boolean isTemplateEditor(Inventory inv) {
        return inv != null && inv.getHolder() instanceof BaseTemplateEditorHolder;
    }

    public void handleClick(Player player, Inventory top, int rawSlot, ClickType click) {
        if (top.getHolder() instanceof BaseTemplateDifficultyListHolder) {
            if (rawSlot == 11) {
                handleDifficultyListClick(player, BaseDifficulty.EASY, click);
            } else if (rawSlot == 13) {
                handleDifficultyListClick(player, BaseDifficulty.MEDIUM, click);
            } else if (rawSlot == 15) {
                handleDifficultyListClick(player, BaseDifficulty.HARD, click);
            }
            return;
        }
        if (!(top.getHolder() instanceof BaseTemplateEditorHolder)) {
            return;
        }
        BaseTemplateEditorHolder h = (BaseTemplateEditorHolder) top.getHolder();
        BaseTemplateDefinition d = h.draft;
        int page = h.page;
        int sel = h.selectedIndex;
        List<DefenseSection> list = d.getSectionsMutable();

        if (rawSlot == SLOT_BACK) {
            openDifficultyList(player);
            return;
        }
        if (rawSlot == SLOT_PAGE_PREV && page > 0) {
            openEditor(player, d, page - 1, sel);
            return;
        }
        if (rawSlot == SLOT_PAGE_NEXT) {
            int maxPage = Math.max(0, (list.size() + SECTIONS_PER_PAGE - 1) / SECTIONS_PER_PAGE - 1);
            if (page < maxPage) {
                openEditor(player, d, page + 1, sel);
            }
            return;
        }
        if (rawSlot == SLOT_ADD_FLAT) {
            if (atCapacityWarn(player, d)) {
                return;
            }
            int ni = insertAfterSelection(d, sel, new DefenseSection(DefenseType.FLAT_WALL, 1));
            openEditor(player, d, page, ni);
            return;
        }
        if (rawSlot == SLOT_ADD_REGEN) {
            if (atCapacityWarn(player, d)) {
                return;
            }
            int ni = insertAfterSelection(d, sel, new DefenseSection(DefenseType.REGEN_WALL, 1));
            openEditor(player, d, page, ni);
            return;
        }
        if (rawSlot == SLOT_ADD_SAND) {
            if (atCapacityWarn(player, d)) {
                return;
            }
            int ni = insertAfterSelection(d, sel, new DefenseSection(DefenseType.SAND_WALL, 1));
            openEditor(player, d, page, ni);
            return;
        }
        if (rawSlot == SLOT_INS_BEFORE_FLAT) {
            if (atCapacityWarn(player, d)) {
                return;
            }
            int ni = insertBeforeSelection(d, sel, new DefenseSection(DefenseType.FLAT_WALL, 1));
            openEditor(player, d, page, ni);
            return;
        }
        if (rawSlot == SLOT_INS_BEFORE_REGEN) {
            if (atCapacityWarn(player, d)) {
                return;
            }
            int ni = insertBeforeSelection(d, sel, new DefenseSection(DefenseType.REGEN_WALL, 1));
            openEditor(player, d, page, ni);
            return;
        }
        if (rawSlot == SLOT_INS_BEFORE_SAND) {
            if (atCapacityWarn(player, d)) {
                return;
            }
            int ni = insertBeforeSelection(d, sel, new DefenseSection(DefenseType.SAND_WALL, 1));
            openEditor(player, d, page, ni);
            return;
        }
        if (rawSlot == SLOT_MOVE_UP) {
            if (sel > 0 && sel < list.size()) {
                Collections.swap(list, sel, sel - 1);
                openEditor(player, d, page, sel - 1);
            } else {
                openEditor(player, d, page, sel);
            }
            return;
        }
        if (rawSlot == SLOT_MOVE_DOWN) {
            if (sel >= 0 && sel < list.size() - 1) {
                Collections.swap(list, sel, sel + 1);
                openEditor(player, d, page, sel + 1);
            } else {
                openEditor(player, d, page, sel);
            }
            return;
        }
        if (rawSlot == SLOT_REMOVE) {
            if (sel >= 0 && sel < list.size()) {
                list.remove(sel);
                if (list.isEmpty()) {
                    sel = -1;
                } else {
                    sel = Math.min(sel, list.size() - 1);
                }
            }
            openEditor(player, d, page, sel);
            return;
        }
        if (rawSlot == SLOT_REP_MINUS) {
            if (sel >= 0 && sel < list.size()) {
                DefenseSection s = list.get(sel);
                if (s.getRepeats() > 1) {
                    list.set(sel, s.withRepeats(s.getRepeats() - 1));
                }
            }
            openEditor(player, d, page, sel);
            return;
        }
        if (rawSlot == SLOT_REP_PLUS) {
            if (sel >= 0 && sel < list.size()) {
                DefenseSection s = list.get(sel);
                int maxR = plugin.getHavocConfig().getBaseGeneratorMaxRepeatPerSection();
                list.set(sel, s.withRepeats(Math.min(maxR, s.getRepeats() + 1)));
            }
            openEditor(player, d, page, sel);
            return;
        }
        if (rawSlot == SLOT_SIZE) {
            int s = d.getSizeChunksOdd();
            if (s == 1) {
                d.setSizeChunksOdd(3);
            } else if (s == 3) {
                d.setSizeChunksOdd(5);
            } else {
                d.setSizeChunksOdd(1);
            }
            openEditor(player, d, page, sel);
            return;
        }
        if (rawSlot == SLOT_SLABS) {
            d.setSlabFloorBetweenWalls(!d.isSlabFloorBetweenWalls());
            openEditor(player, d, page, sel);
            return;
        }
        if (rawSlot == SLOT_SAVE) {
            int maxSec = plugin.getHavocConfig().getBaseGeneratorMaxEditorSections();
            int maxRep = plugin.getHavocConfig().getBaseGeneratorMaxRepeatPerSection();
            List<String> err = BaseTemplateValidation.validate(d, maxSec, maxRep);
            if (!err.isEmpty()) {
                player.sendMessage(ChatColor.RED + err.get(0));
                return;
            }
            try {
                String rel = BaseTemplateSaveCoordinator.saveGeneratedTemplate(plugin, d);
                plugin.getMessages().send(player, "admin.bases.save-success",
                        MessageVars.one(MessageKeys.FILE, rel));
                plugin.getLogService().log("ADMIN_BASE_TEMPLATE_SAVE", player.getName(), d.getDifficulty().name(),
                        player.getLocation(), "file=" + rel);
            } catch (Exception e) {
                plugin.getLogger().warning("Template save failed: " + e.getMessage());
                plugin.getMessages().send(player, "admin.bases.save-failed",
                        MessageVars.one(MessageKeys.REASON, e.getMessage() == null ? "unknown" : e.getMessage()));
            }
            openEditor(player, d, page, sel);
            return;
        }
        if (rawSlot >= SLOT_SECTIONS_START && rawSlot < SLOT_SECTIONS_START + SECTIONS_PER_PAGE) {
            int i = rawSlot - SLOT_SECTIONS_START;
            int idx = page * SECTIONS_PER_PAGE + i;
            if (idx >= 0 && idx < list.size()) {
                openEditor(player, d, page, idx);
            }
            return;
        }
    }

    /**
     * @return new selected index for the inserted row
     */
    private boolean atCapacityWarn(Player player, BaseTemplateDefinition d) {
        int max = plugin.getHavocConfig().getBaseGeneratorMaxEditorSections();
        if (d.getSectionsMutable().size() >= max) {
            plugin.getMessages().send(player, "admin.bases.max-sections",
                    MessageVars.one(MessageKeys.COUNT, String.valueOf(max)));
            return true;
        }
        return false;
    }

    private int insertAfterSelection(BaseTemplateDefinition d, int selectedIndex, DefenseSection section) {
        List<DefenseSection> list = d.getSectionsMutable();
        int insertAt = selectedIndex < 0 ? list.size() : Math.min(list.size(), selectedIndex + 1);
        list.add(insertAt, section);
        return insertAt;
    }

    private int insertBeforeSelection(BaseTemplateDefinition d, int selectedIndex, DefenseSection section) {
        List<DefenseSection> list = d.getSectionsMutable();
        int insertAt = selectedIndex < 0 ? 0 : Math.max(0, selectedIndex);
        list.add(insertAt, section);
        return insertAt;
    }

    private void handleDifficultyListClick(Player player, BaseDifficulty difficulty, ClickType click) {
        if (click == null) {
            return;
        }
        if (click.isShiftClick()) {
            openEditor(player, difficulty);
            return;
        }
        if (click == ClickType.RIGHT) {
            if (deleteCurrentlySelectedVariant(difficulty)) {
                player.sendMessage(ChatColor.YELLOW + "Deleted selected " + difficulty.name() + " variant.");
            } else {
                player.sendMessage(ChatColor.RED + "No selected generated variant to delete for " + difficulty.name() + ".");
            }
            openDifficultyList(player);
            return;
        }
        if (click == ClickType.LEFT) {
            cycleSelectionWrap(difficulty);
            openDifficultyList(player);
            return;
        }
    }

    private ItemStack createDifficultyItem(BaseDifficulty difficulty, Material material, ChatColor color) {
        List<String> variants = listGeneratedVariants(difficulty);
        String selected = plugin.getHavocConfig().getGeneratedSchematicRelative(difficulty);
        int selectedIdx = indexOfVariant(variants, selected);

        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.GRAY + "Shift-click: edit wall layout");
        lore.add(ChatColor.GRAY + "Left: cycle selected (wrap)");
        lore.add(ChatColor.GRAY + "Right: delete currently selected");
        lore.add(ChatColor.DARK_GRAY + " ");
        if (variants.isEmpty()) {
            lore.add(ChatColor.DARK_GRAY + "No generated variants yet.");
        } else {
            for (int i = 0; i < variants.size(); i++) {
                String prefix = i == selectedIdx ? ChatColor.GOLD + "-> " : ChatColor.GRAY + "   ";
                lore.add(prefix + ChatColor.WHITE + shortVariantName(variants.get(i)));
            }
        }
        return icon(material, color + difficulty.name(), lore);
    }

    private List<String> listGeneratedVariants(BaseDifficulty difficulty) {
        List<String> out = new ArrayList<String>();
        File dir = new File(plugin.getDataFolder(), plugin.getHavocConfig().getSchematicsFolder() + "/generated");
        if (!dir.isDirectory()) {
            return out;
        }
        String prefix = difficulty.name().toLowerCase(Locale.ROOT) + "-generated-";
        File[] files = dir.listFiles();
        if (files == null) {
            return out;
        }
        for (File f : files) {
            if (!f.isFile()) {
                continue;
            }
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (!n.endsWith(".schematic")) {
                continue;
            }
            if (!n.startsWith(prefix)) {
                continue;
            }
            out.add("generated/" + f.getName());
        }
        Collections.sort(out, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return a.compareToIgnoreCase(b);
            }
        });
        return out;
    }

    private int indexOfVariant(List<String> variants, String selected) {
        if (selected == null || selected.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < variants.size(); i++) {
            if (selected.equalsIgnoreCase(variants.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private void cycleSelectionWrap(BaseDifficulty difficulty) {
        List<String> variants = listGeneratedVariants(difficulty);
        if (variants.isEmpty()) {
            return;
        }
        int idx = indexOfVariant(variants, plugin.getHavocConfig().getGeneratedSchematicRelative(difficulty));
        if (idx < 0) {
            idx = 0;
        } else {
            idx = (idx + 1) % variants.size();
        }
        setActiveVariant(difficulty, variants.get(idx));
    }

    private boolean deleteCurrentlySelectedVariant(BaseDifficulty difficulty) {
        List<String> variants = listGeneratedVariants(difficulty);
        if (variants.isEmpty()) {
            return false;
        }
        int idx = indexOfVariant(variants, plugin.getHavocConfig().getGeneratedSchematicRelative(difficulty));
        if (idx < 0 || idx >= variants.size()) {
            return false;
        }
        String rel = variants.get(idx);
        File file = new File(plugin.getDataFolder(), plugin.getHavocConfig().getSchematicsFolder() + "/" + rel);
        if (file.isFile() && !file.delete()) {
            return false;
        }
        variants.remove(idx);
        if (variants.isEmpty()) {
            setActiveVariant(difficulty, "");
        } else {
            int next = Math.min(idx, variants.size() - 1);
            setActiveVariant(difficulty, variants.get(next));
        }
        return true;
    }

    private void setActiveVariant(BaseDifficulty difficulty, String relPath) {
        plugin.getConfig().set("generated-schematics." + difficulty.name(), relPath == null ? "" : relPath);
        plugin.saveConfig();
        plugin.getHavocConfig().reload();
    }

    private String shortVariantName(String relPath) {
        int slash = relPath.lastIndexOf('/');
        return slash >= 0 ? relPath.substring(slash + 1) : relPath;
    }

    private static List<String> line(String one) {
        return Collections.singletonList(ChatColor.GRAY + one);
    }

    private static String chunkLabel(int n) {
        return n + " chunks";
    }

    private static String onOff(boolean v) {
        return v ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF";
    }

    private static Material materialFor(DefenseType t) {
        switch (t) {
            case REGEN_WALL:
                return Material.LAVA_BUCKET;
            case SAND_WALL:
                return Material.SAND;
            case FLAT_WALL:
            default:
                return Material.OBSIDIAN;
        }
    }

    private static ItemStack icon(Material mat, String name, List<String> lore) {
        ItemStack stack = new ItemStack(mat, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}