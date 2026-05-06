package com.kartersanamo.havoc.generator;

import com.kartersanamo.havoc.Havoc;
import com.kartersanamo.havoc.base.BaseDifficulty;
import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.world.DataException;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Validates a definition, exports a versioned schematic, updates {@code config.yml}, and persists template YAML.
 */
public final class BaseTemplateSaveCoordinator {

    private BaseTemplateSaveCoordinator() {
    }

    public static String saveGeneratedTemplate(Havoc plugin, BaseTemplateDefinition def) throws IOException, DataException {
        int maxSec = plugin.getHavocConfig().getBaseGeneratorMaxEditorSections();
        int maxRep = plugin.getHavocConfig().getBaseGeneratorMaxRepeatPerSection();
        List<String> errors = BaseTemplateValidation.validate(def, maxSec, maxRep);
        if (!errors.isEmpty()) {
            throw new IOException(errors.get(0));
        }

        int wallH = plugin.getHavocConfig().getBaseGeneratorWallHeightBlocks();
        BaseTemplateResult result = BaseTemplateGenerator.generate(def, wallH);
        CuboidClipboard clip = result.getClipboard();

        File schemRoot = new File(plugin.getDataFolder(), plugin.getHavocConfig().getSchematicsFolder());
        File genDir = new File(schemRoot, "generated");
        if (!genDir.exists() && !genDir.mkdirs()) {
            throw new IOException("Could not create directory: " + genDir);
        }

        String ts = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        String fileName = def.getDifficulty().name().toLowerCase(Locale.ROOT) + "-generated-" + ts + ".schematic";
        File out = new File(genDir, fileName);
        new com.kartersanamo.havoc.world.SchematicService().saveClipboard(out, clip);

        String relative = "generated/" + fileName;
        plugin.getBaseTemplateStore().save(def);

        FileConfiguration cfg = plugin.getConfig();
        cfg.set("generated-schematics." + def.getDifficulty().name(), relative);
        cfg.set("schematic-center-from-min." + def.getDifficulty().name(),
                result.getAnchorX() + "," + result.getAnchorY() + "," + result.getAnchorZ());
        plugin.saveConfig();
        plugin.getHavocConfig().reload();

        plugin.getLogger().info("Saved generated base template for " + def.getDifficulty() + " to " + out.getName()
                + " (anchor " + result.getAnchorX() + "," + result.getAnchorY() + "," + result.getAnchorZ() + ")");
        return relative;
    }
}
