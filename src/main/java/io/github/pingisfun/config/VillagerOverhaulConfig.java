package io.github.pingisfun.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.github.pingisfun.VillagerOverhaul;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VillagerOverhaulConfig {
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(SlotSelection.class, new SlotSelectionAdapter())
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("villager-overhaul.json");

    public int schemaVersion = 1;
    public Curing curing = new Curing();
    public RerollPrevention rerollPrevention = new RerollPrevention();
    public Librarians librarians = new Librarians();
    public Welfare welfare = new Welfare();

    public static VillagerOverhaulConfig load() {
        VillagerOverhaulConfig config = new VillagerOverhaulConfig();

        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                VillagerOverhaulConfig loaded = GSON.fromJson(reader, VillagerOverhaulConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (IOException | RuntimeException exception) {
                VillagerOverhaul.LOGGER.warn("Failed to load {}, using defaults", CONFIG_PATH, exception);
            }
        }

        config.validate();
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            VillagerOverhaul.LOGGER.warn("Failed to save {}", CONFIG_PATH, exception);
        }
    }

    public String set(String key, String value) {
        String normalized = key.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "curing.enabled" -> curing.enabled = parseBoolean(value, curing.enabled);
            case "curing.maxsafecures" -> curing.maxSafeCures = parseInt(value, curing.maxSafeCures);
            case "curing.penaltychancepercent" -> curing.penaltyChancePercent = parseDouble(value, curing.penaltyChancePercent);
            case "curing.slotselection" -> curing.slotSelection = SlotSelection.fromConfig(value);
            case "rerollprevention.enabled" -> rerollPrevention.enabled = parseBoolean(value, rerollPrevention.enabled);
            case "rerollprevention.memoryradius" -> rerollPrevention.memoryRadius = parseInt(value, rerollPrevention.memoryRadius);
            case "rerollprevention.protectedprofessions" -> rerollPrevention.protectedProfessions = parseList(value);
            case "librarians.enabled" -> librarians.enabled = parseBoolean(value, librarians.enabled);
            case "librarians.rareduplicatepreventionenabled" -> librarians.rareDuplicatePreventionEnabled = parseBoolean(value, librarians.rareDuplicatePreventionEnabled);
            case "librarians.duplicatesearchradius" -> librarians.duplicateSearchRadius = parseInt(value, librarians.duplicateSearchRadius);
            case "librarians.rareenchantments" -> librarians.rareEnchantments = parseList(value);
            case "welfare.enabled" -> welfare.enabled = parseBoolean(value, welfare.enabled);
            case "welfare.scanradius" -> welfare.scanRadius = parseInt(value, welfare.scanRadius);
            case "welfare.minbeds" -> welfare.minBeds = parseInt(value, welfare.minBeds);
            case "welfare.minjobsites" -> welfare.minJobSites = parseInt(value, welfare.minJobSites);
            case "welfare.minsafelight" -> welfare.minSafeLight = parseInt(value, welfare.minSafeLight);
            case "welfare.rewardenabled" -> welfare.rewardEnabled = parseBoolean(value, welfare.rewardEnabled);
            case "welfare.rewardreputation" -> welfare.rewardReputation = parseInt(value, welfare.rewardReputation);
            case "welfare.rewardcooldownticks" -> welfare.rewardCooldownTicks = parseInt(value, welfare.rewardCooldownTicks);
            case "welfare.penaltyenabled" -> welfare.penaltyEnabled = parseBoolean(value, welfare.penaltyEnabled);
            case "welfare.penaltyreputation" -> welfare.penaltyReputation = parseInt(value, welfare.penaltyReputation);
            case "welfare.penaltycooldownticks" -> welfare.penaltyCooldownTicks = parseInt(value, welfare.penaltyCooldownTicks);
            default -> throw new IllegalArgumentException("Unknown option: " + key);
        }

        validate();
        save();
        return normalized + " = " + switch (normalized) {
            case "curing.slotselection" -> curing.slotSelection.configName;
            case "curing.penaltychancepercent" -> Double.toString(curing.penaltyChancePercent);
            case "curing.enabled" -> Boolean.toString(curing.enabled);
            case "rerollprevention.protectedprofessions" -> String.join(",", rerollPrevention.protectedProfessions);
            case "librarians.rareenchantments" -> String.join(",", librarians.rareEnchantments);
            default -> value;
        };
    }

    public void validate() {
        if (curing == null) {
            curing = new Curing();
        }

        curing.maxSafeCures = Math.max(0, curing.maxSafeCures);
        curing.penaltyChancePercent = Math.clamp(curing.penaltyChancePercent, 0.0D, 100.0D);
        if (curing.slotSelection == null) {
            curing.slotSelection = SlotSelection.HIGHEST_VALUE;
        }
        if (rerollPrevention == null) {
            rerollPrevention = new RerollPrevention();
        }
        if (librarians == null) {
            librarians = new Librarians();
        }
        if (welfare == null) {
            welfare = new Welfare();
        }

        rerollPrevention.memoryRadius = Math.max(0, rerollPrevention.memoryRadius);
        rerollPrevention.protectedProfessions = normalizeList(rerollPrevention.protectedProfessions, new RerollPrevention().protectedProfessions);
        librarians.duplicateSearchRadius = Math.max(1, librarians.duplicateSearchRadius);
        librarians.rareEnchantments = normalizeList(librarians.rareEnchantments, new Librarians().rareEnchantments);
        welfare.scanRadius = Math.max(1, welfare.scanRadius);
        welfare.minBeds = Math.max(0, welfare.minBeds);
        welfare.minJobSites = Math.max(0, welfare.minJobSites);
        welfare.minSafeLight = Math.clamp(welfare.minSafeLight, 0, 15);
        welfare.rewardReputation = Math.max(0, welfare.rewardReputation);
        welfare.rewardCooldownTicks = Math.max(20, welfare.rewardCooldownTicks);
        welfare.penaltyReputation = Math.max(0, welfare.penaltyReputation);
        welfare.penaltyCooldownTicks = Math.max(20, welfare.penaltyCooldownTicks);
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }
        return fallback;
    }

    private static double parseDouble(String raw, double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static List<String> parseList(String raw) {
        List<String> values = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private static List<String> normalizeList(List<String> values, List<String> fallback) {
        if (values == null) {
            return new ArrayList<>(fallback);
        }

        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                normalized.add(value.trim());
            }
        }
        return normalized;
    }

    public enum SlotSelection {
        RANDOM("random"),
        HIGHEST_VALUE("highestValue"),
        LAST_SLOT("lastSlot");

        public final String configName;

        SlotSelection(String configName) {
            this.configName = configName;
        }

        public static SlotSelection fromConfig(String raw) {
            if (raw != null) {
                for (SlotSelection selection : values()) {
                    if (selection.configName.equalsIgnoreCase(raw)) {
                        return selection;
                    }
                    if (selection.name().equalsIgnoreCase(raw)) {
                        return selection;
                    }
                }
            }
            return HIGHEST_VALUE;
        }

        @Override
        public String toString() {
            return configName;
        }
    }

    public static final class Curing {
        public boolean enabled = true;
        public int maxSafeCures = 1;
        public double penaltyChancePercent = 65.0D;
        public SlotSelection slotSelection = SlotSelection.RANDOM;
    }

    public static final class RerollPrevention {
        public boolean enabled = true;
        public int memoryRadius = 16;
        public List<String> protectedProfessions = new ArrayList<>(List.of(
            "minecraft:armorer",
            "minecraft:butcher",
            "minecraft:cartographer",
            "minecraft:cleric",
            "minecraft:farmer",
            "minecraft:fisherman",
            "minecraft:fletcher",
            "minecraft:leatherworker",
            "minecraft:librarian",
            "minecraft:mason",
            "minecraft:shepherd",
            "minecraft:toolsmith",
            "minecraft:weaponsmith"
        ));
    }

    public static final class Librarians {
        public boolean enabled = true;
        public boolean rareDuplicatePreventionEnabled = true;
        public int duplicateSearchRadius = 48;
        public List<String> rareEnchantments = new ArrayList<>(List.of(
            "minecraft:mending",
            "minecraft:unbreaking",
            "minecraft:efficiency",
            "minecraft:fortune",
            "minecraft:silk_touch"
        ));
    }

    public static final class Welfare {
        public boolean enabled = false;
        public int scanRadius = 32;
        public int minBeds = 4;
        public int minJobSites = 3;
        public int minSafeLight = 8;
        public boolean rewardEnabled = true;
        public int rewardReputation = 5;
        public int rewardCooldownTicks = 24000;
        public boolean penaltyEnabled = true;
        public int penaltyReputation = 2;
        public int penaltyCooldownTicks = 24000;
    }

    private static final class SlotSelectionAdapter implements JsonSerializer<SlotSelection>, JsonDeserializer<SlotSelection> {
        @Override
        public SlotSelection deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return SlotSelection.HIGHEST_VALUE;
            }
            return SlotSelection.fromConfig(json.getAsString());
        }

        @Override
        public JsonElement serialize(SlotSelection src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive((src == null ? SlotSelection.HIGHEST_VALUE : src).configName);
        }
    }
}
