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
        boolean existed = Files.exists(CONFIG_PATH);

        if (existed) {
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
        if (!existed && Files.exists(CONFIG_PATH)) {
            VillagerOverhaul.LOGGER.info("Created default config at {}", CONFIG_PATH);
        }
        return config;
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

    private static List<Double> normalizeChanceList(List<Double> values, List<Double> fallback, int size) {
        List<Double> normalized = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            double value = fallback.get(i);
            if (values != null && i < values.size() && values.get(i) != null) {
                value = values.get(i);
            }
            normalized.add(Math.clamp(value, 0.0D, 100.0D));
        }
        return normalized;
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
        rerollPrevention.affectedProfessions = normalizeList(rerollPrevention.affectedProfessions, new RerollPrevention().affectedProfessions);
        librarians.rareBookBiasChancePercentByLevel = normalizeChanceList(
                librarians.rareBookBiasChancePercentByLevel,
                new Librarians().rareBookBiasChancePercentByLevel,
                5
        );
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
        public List<String> affectedProfessions = new ArrayList<>(List.of(
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
        public boolean rareBookBiasEnabled = false;
        public List<Double> rareBookBiasChancePercentByLevel = new ArrayList<>(List.of(0.0D, 0.0D, 25.0D, 50.0D, 75.0D));
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
