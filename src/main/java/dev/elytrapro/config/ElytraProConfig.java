package dev.elytrapro.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.elytrapro.client.ElytraProClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ElytraProConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ElytraProConfig instance;

    private static final int CONFIG_VERSION = 6;

    /** Old config files are reset once so the new defaults take effect. */
    public int configVersion; // 0 when missing in an old file
    /** Master switch (toggled with the ElytraPro on/off key). OFF = everything stays vanilla. */
    public boolean enabled = true;
    public boolean autoFirework = true;
    public boolean autoElytra = true;
    /** Right click (rocket in hand) on the ground: jump, open wings and fire. */
    public boolean launchFromGround = true;
    /** Minimum ticks between two automatic firework uses. */
    public int cooldownTicks = 10;
    /** Do not use another rocket while one launched by the player is still active. */
    public boolean skipIfBoosting = true;
    /** Ticks in the air (jump held) before Elytra opens. */
    public int minAirTicks = 0;
    /** Only try to open wings when moving downwards (older server rule). Off = open instantly. */
    public boolean onlyWhenFalling = false;
    /** Right click while standing inside a cobweb launches you (any crosshair target). */
    public boolean launchInsideCobweb = true;
    /** If false, right click with a block in hand always places the block (vanilla). */
    public boolean triggerWithBlockInHand = false;
    /** PLACED blocks that launch you when you right-click them. */
    public List<String> triggerBlockTags = List.of("minecraft:logs", "minecraft:planks");
    public List<String> triggerBlocks = List.of("minecraft:cobweb", "minecraft:bedrock");

    /** Right click while your body is inside planks/logs also launches you (same as cobweb). */
    public boolean launchInsidePlanks = true;
    /** Blocks that count as "inside" for launchInsidePlanks (cobweb is handled by launchInsideCobweb). */
    public List<String> insideBlockTags = List.of("minecraft:logs", "minecraft:planks");
    public List<String> insideBlocks = List.of();

    /** Stage 1 (auto knockback hit while gliding). Toggled with the Stage 1 key. */
    public boolean stage1Enabled = false;
    /** Max distance (blocks) from your eyes to the target's hitbox. */
    public double stage1Range = 2.0;
    /** Max angle (degrees) between your view direction and the target. Small = only the player right in front. */
    public double stage1Angle = 20.0;
    /** Ticks Stage 1 keeps trying after the rocket click (waits for target / attack cooldown). */
    public int stage1WaitTicks = 8;
    /** Hotbar slot (1-9) to return to after the hit. 0 = back to the slot you were on. */
    public int stage1ReturnSlot = 1;

    /** Shows why a click was ignored (action bar). */
    public boolean debug = false;

    private transient Matcher triggerMatcher;
    private transient Matcher insideMatcher;

    private static final class Matcher {
        final Set<Block> blocks = new HashSet<>();
        final List<TagKey<Block>> tags = new ArrayList<>();

        Matcher(List<String> blockIds, List<String> tagIds) {
            for (String s : blockIds) {
                Identifier id = Identifier.tryParse(s);
                if (id != null && Registries.BLOCK.containsId(id)) blocks.add(Registries.BLOCK.get(id));
            }
            for (String s : tagIds) {
                Identifier id = Identifier.tryParse(s);
                if (id != null) tags.add(TagKey.of(RegistryKeys.BLOCK, id));
            }
        }

        boolean test(BlockState state) {
            if (blocks.contains(state.getBlock())) return true;
            for (TagKey<Block> tag : tags) {
                if (state.isIn(tag)) return true;
            }
            return false;
        }
    }

    public static ElytraProConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static ElytraProConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("elytrapro.json");
        ElytraProConfig cfg = null;
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path)) {
                cfg = GSON.fromJson(r, ElytraProConfig.class);
            } catch (Exception e) {
                ElytraProClient.LOGGER.error("Could not read elytrapro.json, using defaults", e);
            }
        }
        boolean write = false;
        if (cfg == null || cfg.configVersion < CONFIG_VERSION) {
            cfg = new ElytraProConfig();
            cfg.configVersion = CONFIG_VERSION;
            write = true;
        }
        if (write) {
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(cfg, w);
            } catch (IOException e) {
                ElytraProClient.LOGGER.error("Could not write elytrapro.json", e);
            }
        }
        cfg.cooldownTicks = Math.max(0, cfg.cooldownTicks);
        cfg.minAirTicks = Math.max(0, cfg.minAirTicks);
        if (cfg.triggerBlockTags == null) cfg.triggerBlockTags = List.of();
        if (cfg.triggerBlocks == null) cfg.triggerBlocks = List.of();
        if (cfg.insideBlockTags == null) cfg.insideBlockTags = List.of();
        if (cfg.insideBlocks == null) cfg.insideBlocks = List.of();
        cfg.stage1WaitTicks = Math.max(0, cfg.stage1WaitTicks);
        if (cfg.stage1Range <= 0) cfg.stage1Range = 2.0;
        if (cfg.stage1Angle <= 0) cfg.stage1Angle = 20.0;
        return cfg;
    }

    public boolean isTriggerBlock(BlockState state) {
        if (triggerMatcher == null) triggerMatcher = new Matcher(triggerBlocks, triggerBlockTags);
        return triggerMatcher.test(state);
    }

    public boolean isInsideBlock(BlockState state) {
        if (insideMatcher == null) insideMatcher = new Matcher(insideBlocks, insideBlockTags);
        return insideMatcher.test(state);
    }

    public void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("elytrapro.json");
        try (Writer w = Files.newBufferedWriter(path)) {
            GSON.toJson(this, w);
        } catch (IOException e) {
            ElytraProClient.LOGGER.error("Could not write elytrapro.json", e);
        }
    }
}
