package io.github.pingisfun.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.pingisfun.VillagerOverhaul;
import io.github.pingisfun.config.VillagerOverhaulConfig;
import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class VillagerOverhaulCommands {
    private static final String[] OPTIONS = {
        "curing.enabled",
        "curing.maxSafeCures",
        "curing.penaltyChancePercent",
        "curing.slotSelection",
        "rerollPrevention.enabled",
        "rerollPrevention.memoryRadius",
        "rerollPrevention.protectedProfessions",
        "librarians.enabled",
        "librarians.rareDuplicatePreventionEnabled",
        "librarians.duplicateSearchRadius",
        "librarians.rareEnchantments",
        "welfare.enabled",
        "welfare.scanRadius",
        "welfare.minBeds",
        "welfare.minJobSites",
        "welfare.minSafeLight",
        "welfare.rewardEnabled",
        "welfare.rewardReputation",
        "welfare.rewardCooldownTicks",
        "welfare.penaltyEnabled",
        "welfare.penaltyReputation",
        "welfare.penaltyCooldownTicks"
    };

    private static final String[] BOOLEANS = {"true", "false"};
    private static final String[] SLOT_SELECTIONS = {"random", "highestValue", "lastSlot"};
    private static final String[] GROUPS = {"curing", "rerollPrevention", "librarians", "welfare"};

    private VillagerOverhaulCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            Commands.literal("villageroverhaul")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.literal("reload")
                    .executes(context -> reload(context.getSource())))
                .then(Commands.literal("status")
                    .executes(context -> status(context.getSource()))
                    .then(Commands.argument("group", StringArgumentType.word())
                        .suggests((context, builder) -> suggestMatching(builder, GROUPS))
                        .executes(context -> statusGroup(context.getSource(), StringArgumentType.getString(context, "group")))))
                .then(Commands.literal("set")
                    .then(Commands.argument("option", StringArgumentType.word())
                        .suggests(VillagerOverhaulCommands::suggestOptions)
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                            .suggests(VillagerOverhaulCommands::suggestValues)
                            .executes(context -> set(
                                context.getSource(),
                                StringArgumentType.getString(context, "option"),
                                StringArgumentType.getString(context, "value")
                            )))))
        ));
    }

    private static CompletableFuture<Suggestions> suggestOptions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return suggestMatching(builder, OPTIONS);
    }

    private static CompletableFuture<Suggestions> suggestValues(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String option = StringArgumentType.getString(context, "option").toLowerCase(Locale.ROOT);
        return switch (option) {
            case "curing.enabled", "rerollprevention.enabled", "librarians.enabled",
                "librarians.rareduplicatepreventionenabled", "welfare.enabled", "welfare.rewardenabled", "welfare.penaltyenabled" -> suggestMatching(builder, BOOLEANS);
            case "curing.slotselection" -> suggestMatching(builder, SLOT_SELECTIONS);
            case "curing.maxsafecures" -> suggestMatching(builder, "0", "1", "2", "3");
            case "curing.penaltychancepercent" -> suggestMatching(builder, "0", "25", "50", "75", "100");
            case "rerollprevention.memoryradius", "librarians.duplicatesearchradius", "welfare.scanradius" -> suggestMatching(builder, "16", "32", "48", "64");
            case "welfare.minbeds", "welfare.minjobsites", "welfare.minsafelight", "welfare.rewardreputation",
                "welfare.penaltyreputation" -> suggestMatching(builder, "0", "1", "2", "3", "4", "8");
            case "welfare.rewardcooldownticks", "welfare.penaltycooldownticks" -> suggestMatching(builder, "1200", "6000", "24000");
            case "rerollprevention.protectedprofessions" -> suggestMatching(builder, "minecraft:librarian", "minecraft:librarian,minecraft:toolsmith");
            case "librarians.rareenchantments" -> suggestMatching(builder, "minecraft:mending,minecraft:fortune");
            default -> builder.buildFuture();
        };
    }

    private static CompletableFuture<Suggestions> suggestMatching(SuggestionsBuilder builder, String... candidates) {
        String remaining = builder.getRemainingLowerCase();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(candidate);
            }
        }
        return builder.buildFuture();
    }

    private static int reload(CommandSourceStack source) {
        VillagerOverhaulConfig config = VillagerOverhaul.reloadConfig();
        warnInvalidRegistryIds(source, config);
        source.sendSuccess(() -> Component.empty()
            .append(prefix("Reloaded"))
            .append(Component.literal(" Config saved and runtime values refreshed.").withStyle(ChatFormatting.GRAY))
            .withStyle(style -> style.withHoverEvent(hover("Configuration was reloaded from villager-overhaul.json.\nCuring is " + onOff(config.curing.enabled) + "."))), false);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        VillagerOverhaulConfig config = VillagerOverhaul.config();
        source.sendSuccess(() -> prefix("Status").append(Component.literal(" Hover a module for details.").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> statusLine("curing", config.curing.enabled, "Cure abuse trade penalty settings."), false);
        source.sendSuccess(() -> statusLine("rerollPrevention", config.rerollPrevention.enabled, "Locks first generated offers for protected untraded villagers."), false);
        source.sendSuccess(() -> statusLine("librarians", config.librarians.enabled, "Biases higher-level librarian books toward configured rare enchantments."), false);
        source.sendSuccess(() -> statusLine("welfare", config.welfare.enabled, "Rewards players near maintained villages."), false);
        return 1;
    }

    private static int statusGroup(CommandSourceStack source, String group) {
        VillagerOverhaulConfig config = VillagerOverhaul.config();
        switch (group.toLowerCase(Locale.ROOT)) {
            case "curing" -> {
                VillagerOverhaulConfig.Curing curing = config.curing;
                source.sendSuccess(() -> groupHeader("curing", curing.enabled, "Limits repeated zombie-villager cure discounts."), false);
                source.sendSuccess(() -> valueLine("enabled", curing.enabled, "Enable or disable cure penalty behavior."), false);
                source.sendSuccess(() -> valueLine("maxSafeCures", curing.maxSafeCures, "Cures before penalties can apply."), false);
                source.sendSuccess(() -> valueLine("penaltyChancePercent", curing.penaltyChancePercent + "%", "Chance to remove one eligible trade after excess cures."), false);
                source.sendSuccess(() -> valueLine("slotSelection", curing.slotSelection, "Which trade slot is selected when a penalty applies."), false);
            }
            case "rerollprevention" -> {
                source.sendSuccess(() -> groupHeader("rerollPrevention", config.rerollPrevention.enabled, "Prevents workstation break-and-replace trade rerolling."), false);
                source.sendSuccess(() -> valueLine("enabled", config.rerollPrevention.enabled, "Use vanilla behavior when off."), false);
                source.sendSuccess(() -> valueLine("memoryRadius", config.rerollPrevention.memoryRadius, "Distance from the remembered job site before a profession change can clear stored offers."), false);
                source.sendSuccess(() -> listLine("protectedProfessions", config.rerollPrevention.protectedProfessions, "Comma-separated profession ids affected by reroll prevention."), false);
            }
            case "librarians" -> {
                source.sendSuccess(() -> groupHeader("librarians", config.librarians.enabled, "Controls librarian rare book leveling and duplicate prevention."), false);
                source.sendSuccess(() -> valueLine("enabled", config.librarians.enabled, "Use vanilla librarian book generation when off."), false);
                source.sendSuccess(() -> valueLine("rareDuplicatePreventionEnabled", config.librarians.rareDuplicatePreventionEnabled, "Avoid nearby duplicate rare enchantments when alternatives exist."), false);
                source.sendSuccess(() -> valueLine("duplicateSearchRadius", config.librarians.duplicateSearchRadius, "Radius used to inspect nearby librarians."), false);
                source.sendSuccess(() -> listLine("rareEnchantments", config.librarians.rareEnchantments, "Configured high-tier enchantment ids."), false);
            }
            case "welfare" -> {
                source.sendSuccess(() -> groupHeader("welfare", config.welfare.enabled, "Rewards players for maintained villages."), false);
                source.sendSuccess(() -> valueLine("enabled", config.welfare.enabled, "No welfare scans run when off."), false);
                source.sendSuccess(() -> valueLine("scanRadius", config.welfare.scanRadius, "Radius around each player to inspect."), false);
                source.sendSuccess(() -> valueLine("minBeds", config.welfare.minBeds, "Required safe beds."), false);
                source.sendSuccess(() -> valueLine("minJobSites", config.welfare.minJobSites, "Required workstation blocks."), false);
                source.sendSuccess(() -> valueLine("minSafeLight", config.welfare.minSafeLight, "Minimum block light at counted beds."), false);
                source.sendSuccess(() -> valueLine("rewardEnabled", config.welfare.rewardEnabled, "Apply positive gossip when a nearby village passes welfare checks."), false);
                source.sendSuccess(() -> valueLine("rewardReputation", config.welfare.rewardReputation, "Positive villager gossip added for the player."), false);
                source.sendSuccess(() -> valueLine("rewardCooldownTicks", config.welfare.rewardCooldownTicks, "Per-player reward cooldown."), false);
                source.sendSuccess(() -> valueLine("penaltyEnabled", config.welfare.penaltyEnabled, "Apply minor negative gossip when a nearby village fails welfare checks."), false);
                source.sendSuccess(() -> valueLine("penaltyReputation", config.welfare.penaltyReputation, "Negative villager gossip added for poor village welfare."), false);
                source.sendSuccess(() -> valueLine("penaltyCooldownTicks", config.welfare.penaltyCooldownTicks, "Per-player poor welfare penalty cooldown."), false);
            }
            default -> {
                source.sendFailure(error("Unknown status group: " + group, "Valid groups: " + String.join(", ", GROUPS)));
                return 0;
            }
        }
        return 1;
    }

    private static String onOff(boolean enabled) {
        return enabled ? "on" : "off";
    }

    private static void warnInvalidRegistryIds(CommandSourceStack source, VillagerOverhaulConfig config) {
        for (String raw : config.rerollPrevention.protectedProfessions) {
            Identifier id = Identifier.tryParse(raw);
            if (id == null || !BuiltInRegistries.VILLAGER_PROFESSION.containsKey(id)) {
                VillagerOverhaul.LOGGER.warn("Ignoring unknown villager profession id in config at runtime: {}", raw);
            }
        }

        Registry<Enchantment> enchantments = source.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (String raw : config.librarians.rareEnchantments) {
            Identifier id = Identifier.tryParse(raw);
            if (id == null || !enchantments.containsKey(id)) {
                VillagerOverhaul.LOGGER.warn("Ignoring unknown enchantment id in config at runtime: {}", raw);
            }
        }
    }

    private static int set(CommandSourceStack source, String option, String value) {
        try {
            String changed = VillagerOverhaul.config().set(option, value);
            source.sendSuccess(() -> prefix("Updated")
                .append(Component.literal(" " + changed).withStyle(ChatFormatting.AQUA))
                .withStyle(style -> style.withHoverEvent(hover("Saved to villager-overhaul.json.\nUse /villageroverhaul status " + optionGroup(option) + " to inspect this group."))), false);
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(error(exception.getMessage(), "Use /villageroverhaul set <option> <value>.\nTab completion lists known options."));
            return 0;
        }
    }

    private static MutableComponent prefix(String label) {
        return Component.literal("[Villager Overhaul] ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(label).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
    }

    private static MutableComponent statusLine(String name, boolean enabled, String tooltip) {
        return Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal(name).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
            .append(state(enabled))
            .withStyle(style -> style
                .withHoverEvent(hover(tooltip + "\nClick to open this module."))
                .withClickEvent(new ClickEvent.RunCommand("/villageroverhaul status " + name)));
    }

    private static MutableComponent groupHeader(String name, boolean enabled, String tooltip) {
        return Component.literal(name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
            .append(state(enabled))
            .withStyle(style -> style.withHoverEvent(hover(tooltip)));
    }

    private static MutableComponent valueLine(String key, Object value, String tooltip) {
        return Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal(key).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" = ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(String.valueOf(value)).withStyle(valueColor(value)))
            .withStyle(style -> style.withHoverEvent(hover(tooltip)));
    }

    private static MutableComponent listLine(String key, Iterable<String> values, String tooltip) {
        return valueLine(key, String.join(", ", values), tooltip);
    }

    private static MutableComponent state(boolean enabled) {
        return Component.literal(onOff(enabled).toUpperCase(Locale.ROOT)).withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static ChatFormatting valueColor(Object value) {
        if (value instanceof Boolean enabled) {
            return enabled ? ChatFormatting.GREEN : ChatFormatting.RED;
        }
        if (value instanceof Number) {
            return ChatFormatting.AQUA;
        }
        return ChatFormatting.WHITE;
    }

    private static HoverEvent hover(String text) {
        return new HoverEvent.ShowText(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    private static MutableComponent error(String message, String tooltip) {
        return Component.literal(message).withStyle(ChatFormatting.RED)
            .withStyle(style -> style.withHoverEvent(hover(tooltip)));
    }

    private static String optionGroup(String option) {
        int dot = option.indexOf('.');
        return dot > 0 ? option.substring(0, dot) : option;
    }
}
