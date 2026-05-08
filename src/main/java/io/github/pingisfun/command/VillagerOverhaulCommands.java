package io.github.pingisfun.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.pingisfun.VillagerOverhaul;
import io.github.pingisfun.config.VillagerOverhaulConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class VillagerOverhaulCommands {
    private static final String[] COMMON_PERCENTAGES = {"0", "25", "50", "75", "100"};

    private VillagerOverhaulCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("villagerrebalance")
                        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .executes(context -> help(context.getSource()))
                        .then(Commands.literal("help")
                                .executes(context -> help(context.getSource())))
                        .then(Commands.literal("reload")
                                .executes(context -> reload(context.getSource())))
                        .then(curingCommands())
                        .then(rerollCommands())
                        .then(librarianCommands())
                        .then(welfareCommands())
        ));
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> prefix("Help")
                .append(Component.literal(" Click a line to run or inspect a command.").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> helpLine("/villagerrebalance reload", "Reload villager-rebalance.json."), false);
        source.sendSuccess(() -> helpLine("/villagerrebalance curing", "Show or configure cure penalty behavior."), false);
        source.sendSuccess(() -> helpLine("/villagerrebalance reroll", "Show or configure workstation reroll prevention."), false);
        source.sendSuccess(() -> helpLine("/villagerrebalance librarians", "Show or configure librarian book behavior."), false);
        source.sendSuccess(() -> helpLine("/villagerrebalance welfare", "Show or configure village welfare checks."), false);
        return 1;
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

    private static LiteralArgumentBuilder<CommandSourceStack> curingCommands() {
        return Commands.literal("curing")
                .executes(context -> statusGroup(context.getSource(), "curing"))
                .then(Commands.literal("enabled")
                        .executes(context -> showValue(context.getSource(), "curing.enabled", VillagerOverhaul.config().curing.enabled, "Enable or disable cure penalty behavior."))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> update(context.getSource(), "curing.enabled", () ->
                                        VillagerOverhaul.config().curing.enabled = BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("max-safe-cures")
                        .executes(context -> showValue(context.getSource(), "curing.maxSafeCures", VillagerOverhaul.config().curing.maxSafeCures, "Cures before penalties can apply."))
                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                .executes(context -> update(context.getSource(), "curing.maxSafeCures", () ->
                                        VillagerOverhaul.config().curing.maxSafeCures = IntegerArgumentType.getInteger(context, "value")))))
                .then(Commands.literal("penalty-chance")
                        .executes(context -> showValue(context.getSource(), "curing.penaltyChancePercent", VillagerOverhaul.config().curing.penaltyChancePercent + "%", "Chance to remove one eligible trade after excess cures."))
                        .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0D, 100.0D))
                                .suggests((context, builder) -> suggestMatching(builder, COMMON_PERCENTAGES))
                                .executes(context -> update(context.getSource(), "curing.penaltyChancePercent", () ->
                                        VillagerOverhaul.config().curing.penaltyChancePercent = DoubleArgumentType.getDouble(context, "percent")))))
                .then(Commands.literal("slot-selection")
                        .executes(context -> showValue(context.getSource(), "curing.slotSelection", VillagerOverhaul.config().curing.slotSelection, "Which trade slot is selected when a penalty applies."))
                        .then(Commands.literal("random")
                                .executes(context -> update(context.getSource(), "curing.slotSelection", () ->
                                        VillagerOverhaul.config().curing.slotSelection = VillagerOverhaulConfig.SlotSelection.RANDOM)))
                        .then(Commands.literal("highest-value")
                                .executes(context -> update(context.getSource(), "curing.slotSelection", () ->
                                        VillagerOverhaul.config().curing.slotSelection = VillagerOverhaulConfig.SlotSelection.HIGHEST_VALUE)))
                        .then(Commands.literal("last-slot")
                                .executes(context -> update(context.getSource(), "curing.slotSelection", () ->
                                        VillagerOverhaul.config().curing.slotSelection = VillagerOverhaulConfig.SlotSelection.LAST_SLOT))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> rerollCommands() {
        return Commands.literal("reroll")
                .executes(context -> statusGroup(context.getSource(), "reroll"))
                .then(Commands.literal("enabled")
                        .executes(context -> showValue(context.getSource(), "rerollPrevention.enabled", VillagerOverhaul.config().rerollPrevention.enabled, "Use vanilla behavior when off."))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> update(context.getSource(), "rerollPrevention.enabled", () ->
                                        VillagerOverhaul.config().rerollPrevention.enabled = BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("memory-radius")
                        .executes(context -> showValue(context.getSource(), "rerollPrevention.memoryRadius", VillagerOverhaul.config().rerollPrevention.memoryRadius, "Distance from the remembered job site before a profession change can clear stored offers."))
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(0))
                                .executes(context -> update(context.getSource(), "rerollPrevention.memoryRadius", () ->
                                        VillagerOverhaul.config().rerollPrevention.memoryRadius = IntegerArgumentType.getInteger(context, "blocks")))))
                .then(Commands.literal("affected-professions")
                        .executes(context -> showList(context.getSource(), "rerollPrevention.affectedProfessions", VillagerOverhaul.config().rerollPrevention.affectedProfessions, "Profession ids affected by reroll prevention."))
                        .then(Commands.literal("add")
                                .then(Commands.argument("profession", IdentifierArgument.id())
                                        .suggests(VillagerOverhaulCommands::suggestProfessions)
                                        .executes(context -> addProfession(context.getSource(), IdentifierArgument.getId(context, "profession")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("profession", IdentifierArgument.id())
                                        .suggests((context, builder) -> suggestValues(builder, VillagerOverhaul.config().rerollPrevention.affectedProfessions))
                                        .executes(context -> removeValue(
                                                context.getSource(),
                                                "rerollPrevention.affectedProfessions",
                                                VillagerOverhaul.config().rerollPrevention.affectedProfessions,
                                                IdentifierArgument.getId(context, "profession").toString()
                                        ))))
                        .then(Commands.literal("clear")
                                .executes(context -> clearValues(
                                        context.getSource(),
                                        "rerollPrevention.affectedProfessions",
                                        VillagerOverhaul.config().rerollPrevention.affectedProfessions
                                ))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> librarianCommands() {
        return Commands.literal("librarians")
                .executes(context -> statusGroup(context.getSource(), "librarians"))
                .then(Commands.literal("enabled")
                        .executes(context -> showValue(context.getSource(), "librarians.enabled", VillagerOverhaul.config().librarians.enabled, "Use vanilla librarian book generation when off."))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> update(context.getSource(), "librarians.enabled", () ->
                                        VillagerOverhaul.config().librarians.enabled = BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("rare-book-bias")
                        .executes(context -> showLibrarianRareBookBias(context.getSource()))
                        .then(Commands.literal("enabled")
                                .executes(context -> showValue(context.getSource(), "librarians.rareBookBiasEnabled", VillagerOverhaul.config().librarians.rareBookBiasEnabled, "Bias higher-level book trades toward configured rare enchantments."))
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> update(context.getSource(), "librarians.rareBookBiasEnabled", () ->
                                                VillagerOverhaul.config().librarians.rareBookBiasEnabled = BoolArgumentType.getBool(context, "enabled")))))
                        .then(Commands.literal("chance")
                                .executes(context -> showList(context.getSource(), "librarians.rareBookBiasChancePercentByLevel", VillagerOverhaul.config().librarians.rareBookBiasChancePercentByLevel, "Chances for villager levels 1, 2, 3, 4, and 5."))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                        .executes(context -> showLibrarianBiasChance(context.getSource(), IntegerArgumentType.getInteger(context, "level")))
                                        .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0D, 100.0D))
                                                .suggests((context, builder) -> suggestMatching(builder, COMMON_PERCENTAGES))
                                                .executes(context -> updateLibrarianBiasChance(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "level"),
                                                        DoubleArgumentType.getDouble(context, "percent")
                                                ))))))
                .then(Commands.literal("rare-duplicates")
                        .executes(context -> showLibrarianRareDuplicates(context.getSource()))
                        .then(Commands.literal("enabled")
                                .executes(context -> showValue(context.getSource(), "librarians.rareDuplicatePreventionEnabled", VillagerOverhaul.config().librarians.rareDuplicatePreventionEnabled, "Avoid nearby duplicate rare enchantments when alternatives exist."))
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> update(context.getSource(), "librarians.rareDuplicatePreventionEnabled", () ->
                                                VillagerOverhaul.config().librarians.rareDuplicatePreventionEnabled = BoolArgumentType.getBool(context, "enabled")))))
                        .then(Commands.literal("search-radius")
                                .executes(context -> showValue(context.getSource(), "librarians.duplicateSearchRadius", VillagerOverhaul.config().librarians.duplicateSearchRadius, "Radius used to inspect nearby librarians."))
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(1))
                                        .executes(context -> update(context.getSource(), "librarians.duplicateSearchRadius", () ->
                                                VillagerOverhaul.config().librarians.duplicateSearchRadius = IntegerArgumentType.getInteger(context, "blocks"))))))
                .then(Commands.literal("rare-enchantments")
                        .executes(context -> showList(context.getSource(), "librarians.rareEnchantments", VillagerOverhaul.config().librarians.rareEnchantments, "Configured high-tier enchantment ids."))
                        .then(Commands.literal("add")
                                .then(Commands.argument("enchantment", IdentifierArgument.id())
                                        .suggests(VillagerOverhaulCommands::suggestEnchantments)
                                        .executes(context -> addEnchantment(context.getSource(), IdentifierArgument.getId(context, "enchantment")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("enchantment", IdentifierArgument.id())
                                        .suggests((context, builder) -> suggestValues(builder, VillagerOverhaul.config().librarians.rareEnchantments))
                                        .executes(context -> removeValue(
                                                context.getSource(),
                                                "librarians.rareEnchantments",
                                                VillagerOverhaul.config().librarians.rareEnchantments,
                                                IdentifierArgument.getId(context, "enchantment").toString()
                                        ))))
                        .then(Commands.literal("clear")
                                .executes(context -> clearValues(
                                        context.getSource(),
                                        "librarians.rareEnchantments",
                                        VillagerOverhaul.config().librarians.rareEnchantments
                                ))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> welfareCommands() {
        return Commands.literal("welfare")
                .executes(context -> statusGroup(context.getSource(), "welfare"))
                .then(Commands.literal("enabled")
                        .executes(context -> showValue(context.getSource(), "welfare.enabled", VillagerOverhaul.config().welfare.enabled, "No welfare scans run when off."))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> update(context.getSource(), "welfare.enabled", () ->
                                        VillagerOverhaul.config().welfare.enabled = BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("scan-radius")
                        .executes(context -> showValue(context.getSource(), "welfare.scanRadius", VillagerOverhaul.config().welfare.scanRadius, "Radius around each player to inspect."))
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(1))
                                .executes(context -> update(context.getSource(), "welfare.scanRadius", () ->
                                        VillagerOverhaul.config().welfare.scanRadius = IntegerArgumentType.getInteger(context, "blocks")))))
                .then(Commands.literal("min-beds")
                        .executes(context -> showValue(context.getSource(), "welfare.minBeds", VillagerOverhaul.config().welfare.minBeds, "Required safe beds."))
                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                .executes(context -> update(context.getSource(), "welfare.minBeds", () ->
                                        VillagerOverhaul.config().welfare.minBeds = IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("min-job-sites")
                        .executes(context -> showValue(context.getSource(), "welfare.minJobSites", VillagerOverhaul.config().welfare.minJobSites, "Required workstation blocks."))
                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                .executes(context -> update(context.getSource(), "welfare.minJobSites", () ->
                                        VillagerOverhaul.config().welfare.minJobSites = IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("min-safe-light")
                        .executes(context -> showValue(context.getSource(), "welfare.minSafeLight", VillagerOverhaul.config().welfare.minSafeLight, "Minimum block light at counted beds."))
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 15))
                                .executes(context -> update(context.getSource(), "welfare.minSafeLight", () ->
                                        VillagerOverhaul.config().welfare.minSafeLight = IntegerArgumentType.getInteger(context, "level")))))
                .then(Commands.literal("reward")
                        .executes(context -> showWelfareReward(context.getSource()))
                        .then(Commands.literal("enabled")
                                .executes(context -> showValue(context.getSource(), "welfare.rewardEnabled", VillagerOverhaul.config().welfare.rewardEnabled, "Apply positive gossip when a nearby village passes welfare checks."))
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> update(context.getSource(), "welfare.rewardEnabled", () ->
                                                VillagerOverhaul.config().welfare.rewardEnabled = BoolArgumentType.getBool(context, "enabled")))))
                        .then(Commands.literal("reputation")
                                .executes(context -> showValue(context.getSource(), "welfare.rewardReputation", VillagerOverhaul.config().welfare.rewardReputation, "Positive villager gossip added for the player."))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(context -> update(context.getSource(), "welfare.rewardReputation", () ->
                                                VillagerOverhaul.config().welfare.rewardReputation = IntegerArgumentType.getInteger(context, "amount")))))
                        .then(Commands.literal("cooldown")
                                .executes(context -> showValue(context.getSource(), "welfare.rewardCooldownTicks", VillagerOverhaul.config().welfare.rewardCooldownTicks, "Per-player reward cooldown."))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(20))
                                        .executes(context -> update(context.getSource(), "welfare.rewardCooldownTicks", () ->
                                                VillagerOverhaul.config().welfare.rewardCooldownTicks = IntegerArgumentType.getInteger(context, "ticks"))))))
                .then(Commands.literal("penalty")
                        .executes(context -> showWelfarePenalty(context.getSource()))
                        .then(Commands.literal("enabled")
                                .executes(context -> showValue(context.getSource(), "welfare.penaltyEnabled", VillagerOverhaul.config().welfare.penaltyEnabled, "Apply minor negative gossip when a nearby village fails welfare checks."))
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> update(context.getSource(), "welfare.penaltyEnabled", () ->
                                                VillagerOverhaul.config().welfare.penaltyEnabled = BoolArgumentType.getBool(context, "enabled")))))
                        .then(Commands.literal("reputation")
                                .executes(context -> showValue(context.getSource(), "welfare.penaltyReputation", VillagerOverhaul.config().welfare.penaltyReputation, "Negative villager gossip added for poor village welfare."))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(context -> update(context.getSource(), "welfare.penaltyReputation", () ->
                                                VillagerOverhaul.config().welfare.penaltyReputation = IntegerArgumentType.getInteger(context, "amount")))))
                        .then(Commands.literal("cooldown")
                                .executes(context -> showValue(context.getSource(), "welfare.penaltyCooldownTicks", VillagerOverhaul.config().welfare.penaltyCooldownTicks, "Per-player poor welfare penalty cooldown."))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(20))
                                        .executes(context -> update(context.getSource(), "welfare.penaltyCooldownTicks", () ->
                                                VillagerOverhaul.config().welfare.penaltyCooldownTicks = IntegerArgumentType.getInteger(context, "ticks"))))));
    }

    private static int reload(CommandSourceStack source) {
        VillagerOverhaulConfig config = VillagerOverhaul.reloadConfig();
        warnInvalidRegistryIds(source, config);
        source.sendSuccess(() -> Component.empty()
                .append(prefix("Reloaded"))
                .append(Component.literal(" Config saved and runtime values refreshed.").withStyle(ChatFormatting.GRAY))
                .withStyle(style -> style.withHoverEvent(hover("Configuration was reloaded from villager-rebalance.json.\nCuring is " + onOff(config.curing.enabled) + "."))), false);
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
            case "reroll" -> {
                source.sendSuccess(() -> groupHeader("rerollPrevention", config.rerollPrevention.enabled, "Prevents workstation break-and-replace trade rerolling."), false);
                source.sendSuccess(() -> valueLine("enabled", config.rerollPrevention.enabled, "Use vanilla behavior when off."), false);
                source.sendSuccess(() -> valueLine("memoryRadius", config.rerollPrevention.memoryRadius, "Distance from the remembered job site before a profession change can clear stored offers."), false);
                source.sendSuccess(() -> listLine("affectedProfessions", config.rerollPrevention.affectedProfessions, "Profession ids affected by reroll prevention."), false);
            }
            case "librarians" -> {
                source.sendSuccess(() -> groupHeader("librarians", config.librarians.enabled, "Controls librarian rare book bias and duplicate prevention."), false);
                source.sendSuccess(() -> valueLine("enabled", config.librarians.enabled, "Use vanilla librarian book generation when off."), false);
                source.sendSuccess(() -> valueLine("rareBookBiasEnabled", config.librarians.rareBookBiasEnabled, "Bias higher-level book trades toward configured rare enchantments."), false);
                source.sendSuccess(() -> listLine("rareBookBiasChancePercentByLevel", config.librarians.rareBookBiasChancePercentByLevel, "Chances for villager levels 1, 2, 3, 4, and 5."), false);
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
            default -> throw new IllegalArgumentException("Unknown module: " + group);
        }
        return 1;
    }

    private static int showLibrarianRareBookBias(CommandSourceStack source) {
        VillagerOverhaulConfig.Librarians librarians = VillagerOverhaul.config().librarians;
        source.sendSuccess(() -> groupHeader("rareBookBias", librarians.rareBookBiasEnabled, "Controls configured rare-book bias."), false);
        source.sendSuccess(() -> valueLine("enabled", librarians.rareBookBiasEnabled, "Bias higher-level book trades toward configured rare enchantments."), false);
        source.sendSuccess(() -> listLine("chancePercentByLevel", librarians.rareBookBiasChancePercentByLevel, "Chances for villager levels 1, 2, 3, 4, and 5."), false);
        return 1;
    }

    private static int showLibrarianBiasChance(CommandSourceStack source, int level) {
        List<Double> chances = VillagerOverhaul.config().librarians.rareBookBiasChancePercentByLevel;
        double chance = level <= chances.size() ? chances.get(level - 1) : 0.0D;
        return showValue(source, "librarians.rareBookBiasChancePercentByLevel[" + level + "]", chance + "%", "Chance for level " + level + " librarian book trades to use configured rare enchantments.");
    }

    private static int showLibrarianRareDuplicates(CommandSourceStack source) {
        VillagerOverhaulConfig.Librarians librarians = VillagerOverhaul.config().librarians;
        source.sendSuccess(() -> groupHeader("rareDuplicates", librarians.rareDuplicatePreventionEnabled, "Controls nearby duplicate rare-book prevention."), false);
        source.sendSuccess(() -> valueLine("enabled", librarians.rareDuplicatePreventionEnabled, "Avoid nearby duplicate rare enchantments when alternatives exist."), false);
        source.sendSuccess(() -> valueLine("searchRadius", librarians.duplicateSearchRadius, "Radius used to inspect nearby librarians."), false);
        return 1;
    }

    private static int showWelfareReward(CommandSourceStack source) {
        VillagerOverhaulConfig.Welfare welfare = VillagerOverhaul.config().welfare;
        source.sendSuccess(() -> groupHeader("reward", welfare.rewardEnabled, "Positive gossip for maintained villages."), false);
        source.sendSuccess(() -> valueLine("enabled", welfare.rewardEnabled, "Apply positive gossip when a nearby village passes welfare checks."), false);
        source.sendSuccess(() -> valueLine("reputation", welfare.rewardReputation, "Positive villager gossip added for the player."), false);
        source.sendSuccess(() -> valueLine("cooldownTicks", welfare.rewardCooldownTicks, "Per-player reward cooldown."), false);
        return 1;
    }

    private static int showWelfarePenalty(CommandSourceStack source) {
        VillagerOverhaulConfig.Welfare welfare = VillagerOverhaul.config().welfare;
        source.sendSuccess(() -> groupHeader("penalty", welfare.penaltyEnabled, "Negative gossip for poor village welfare."), false);
        source.sendSuccess(() -> valueLine("enabled", welfare.penaltyEnabled, "Apply minor negative gossip when a nearby village fails welfare checks."), false);
        source.sendSuccess(() -> valueLine("reputation", welfare.penaltyReputation, "Negative villager gossip added for poor village welfare."), false);
        source.sendSuccess(() -> valueLine("cooldownTicks", welfare.penaltyCooldownTicks, "Per-player poor welfare penalty cooldown."), false);
        return 1;
    }

    private static int showValue(CommandSourceStack source, String key, Object value, String tooltip) {
        source.sendSuccess(() -> prefix("Current")
                .append(Component.literal(" " + key + " = ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(value)).withStyle(valueColor(value)))
                .withStyle(style -> style.withHoverEvent(hover(tooltip))), false);
        return 1;
    }

    private static int showList(CommandSourceStack source, String key, Iterable<?> values, String tooltip) {
        StringBuilder joined = new StringBuilder();
        for (Object value : values) {
            if (!joined.isEmpty()) {
                joined.append(", ");
            }
            joined.append(value);
        }
        return showValue(source, key, joined.isEmpty() ? "<empty>" : joined.toString(), tooltip);
    }

    private static String onOff(boolean enabled) {
        return enabled ? "on" : "off";
    }

    private static void warnInvalidRegistryIds(CommandSourceStack source, VillagerOverhaulConfig config) {
        for (String raw : config.rerollPrevention.affectedProfessions) {
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

    private static int update(CommandSourceStack source, String setting, Runnable mutation) {
        mutation.run();
        VillagerOverhaulConfig config = VillagerOverhaul.config();
        config.validate();
        config.save();
        source.sendSuccess(() -> prefix("Updated")
                .append(Component.literal(" " + setting).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" saved.").withStyle(ChatFormatting.GRAY))
                .withStyle(style -> style.withHoverEvent(hover("Saved to villager-rebalance.json.\nUse /villagerrebalance " + commandGroupFor(setting) + " to inspect this group."))), false);
        return 1;
    }

    private static int updateLibrarianBiasChance(CommandSourceStack source, int level, double percent) {
        return update(source, "librarians.rareBookBiasChancePercentByLevel", () -> {
            List<Double> chances = VillagerOverhaul.config().librarians.rareBookBiasChancePercentByLevel;
            while (chances.size() < 5) {
                chances.add(0.0D);
            }
            chances.set(level - 1, percent);
        });
    }

    private static int addProfession(CommandSourceStack source, Identifier id) {
        if (!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(id)) {
            source.sendFailure(error("Unknown villager profession: " + id, "Use a valid profession id such as minecraft:librarian."));
            return 0;
        }
        return addValue(source, "rerollPrevention.affectedProfessions", VillagerOverhaul.config().rerollPrevention.affectedProfessions, id.toString());
    }

    private static int addEnchantment(CommandSourceStack source, Identifier id) {
        Registry<Enchantment> enchantments = source.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        if (!enchantments.containsKey(id)) {
            source.sendFailure(error("Unknown enchantment: " + id, "Use a valid enchantment id such as minecraft:mending."));
            return 0;
        }
        return addValue(source, "librarians.rareEnchantments", VillagerOverhaul.config().librarians.rareEnchantments, id.toString());
    }

    private static int addValue(CommandSourceStack source, String setting, List<String> values, String value) {
        return update(source, setting, () -> {
            if (!values.contains(value)) {
                values.add(value);
            }
        });
    }

    private static int removeValue(CommandSourceStack source, String setting, List<String> values, String value) {
        return update(source, setting, () -> values.remove(value));
    }

    private static int clearValues(CommandSourceStack source, String setting, List<String> values) {
        return update(source, setting, values::clear);
    }

    private static CompletableFuture<Suggestions> suggestProfessions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        for (Identifier id : BuiltInRegistries.VILLAGER_PROFESSION.keySet()) {
            if (!"minecraft:none".equals(id.toString())) {
                suggestIfMatching(builder, id.toString());
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestEnchantments(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        Registry<Enchantment> enchantments = context.getSource().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (Identifier id : enchantments.keySet()) {
            suggestIfMatching(builder, id.toString());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestValues(SuggestionsBuilder builder, List<String> values) {
        for (String value : values) {
            suggestIfMatching(builder, value);
        }
        return builder.buildFuture();
    }

    private static void suggestIfMatching(SuggestionsBuilder builder, String candidate) {
        if (candidate.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
            builder.suggest(candidate);
        }
    }

    private static MutableComponent prefix(String label) {
        return Component.literal("[Villager Rebalance] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(label).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
    }

    private static MutableComponent helpLine(String command, String tooltip) {
        return Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(command).withStyle(ChatFormatting.YELLOW))
                .withStyle(style -> style
                        .withHoverEvent(hover(tooltip))
                        .withClickEvent(new ClickEvent.SuggestCommand(command)));
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

    private static MutableComponent listLine(String key, Iterable<?> values, String tooltip) {
        StringBuilder joined = new StringBuilder();
        for (Object value : values) {
            if (!joined.isEmpty()) {
                joined.append(", ");
            }
            joined.append(value);
        }
        return valueLine(key, joined.toString(), tooltip);
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

    private static String commandGroupFor(String setting) {
        if (setting.startsWith("rerollPrevention.")) {
            return "reroll";
        }
        int dot = setting.indexOf('.');
        return dot > 0 ? setting.substring(0, dot) : setting;
    }
}
