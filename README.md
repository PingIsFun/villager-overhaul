# Villager Overhaul

Villager Overhaul is a server-side Fabric mod that makes villager progression harder to exploit while keeping the mechanics configurable. It targets Minecraft 26.1 and does not require a client-side install.

## Features

### Cure Discount Limits

Repeated zombie villager curing can remove an eligible trade after a configurable number of safe cures. This keeps one cure useful while reducing repeated cure abuse.

Config options:

- `curing.enabled`: enables the module.
- `curing.maxSafeCures`: cures allowed before penalties can happen.
- `curing.penaltyChancePercent`: chance that an excess cure removes one trade.
- `curing.slotSelection`: chooses the removed trade with `random`, `highestValue`, or `lastSlot`.

### Reroll Prevention

Untraded villagers in affected professions remember their first generated offers. Breaking and replacing a workstation within the memory radius restores those offers instead of rolling new ones.

Reroll locks are stored per profession, so a villager can remember separate affected-profession offers for jobs such as fletcher and librarian. Trading with the villager clears the stored locks.

Config options:

- `rerollPrevention.enabled`: enables the module.
- `rerollPrevention.memoryRadius`: distance from the remembered job site before old offer locks can be cleared by a profession change.
- `rerollPrevention.affectedProfessions`: comma-separated villager profession ids affected by reroll prevention.

### Librarian Rare Book Tuning

Librarian book trades can be biased toward configured rare enchantments when `librarians.rareBookBiasEnabled` is on. The chance is configured per villager level with `librarians.rareBookBiasChancePercentByLevel`.

The list has five comma-separated percentages in level order:

```text
level 1, level 2, level 3, level 4, level 5
```

The default is:

```text
0,0,25,50,75
```

That means level 1 and 2 librarians keep vanilla book generation, level 3 has a 25% chance to use a configured rare enchantment, level 4 has a 50% chance, and level 5 has a 75% chance.

Duplicate prevention is controlled separately with `librarians.rareDuplicatePreventionEnabled`. When rare-book bias is on, duplicate prevention avoids choosing a configured rare enchantment that already exists nearby when another configured rare enchantment is available. When rare-book bias is off, duplicate prevention can still replace a newly generated configured rare book if that same rare enchantment already exists nearby.

Config options:

- `librarians.enabled`: enables the module.
- `librarians.rareBookBiasEnabled`: enables the level-based chance to replace book trades with configured rare enchantments.
- `librarians.rareBookBiasChancePercentByLevel`: five comma-separated percentages for levels 1 through 5.
- `librarians.rareDuplicatePreventionEnabled`: avoids nearby duplicate rare books when possible.
- `librarians.duplicateSearchRadius`: radius used to inspect nearby librarians.
- `librarians.rareEnchantments`: comma-separated enchantment ids that count as rare.

### Village Welfare

Village welfare can reward players for maintaining villages and penalize players near poor villages. It works as a periodic server-side scan around each player while `welfare.enabled` is on.

For each player, the mod checks a box around the player using `welfare.scanRadius`. A village only counts as maintained when all of these are true:

- At least one living villager is nearby.
- The area contains at least `welfare.minBeds` beds.
- Counted beds have at least `welfare.minSafeLight` local brightness.
- The area contains at least `welfare.minJobSites` workstation blocks.

If the scan passes and rewards are enabled, every nearby villager receives `welfare.rewardReputation` minor positive gossip for that player. This is vanilla villager reputation data, so it can improve nearby trade prices over time. The reward can only happen once per player per `welfare.rewardCooldownTicks`.

If the scan fails, penalties are enabled, and there is at least one nearby villager, every nearby villager receives `welfare.penaltyReputation` minor negative gossip for that player. This can make nearby trades worse. The penalty can only happen once per player per `welfare.penaltyCooldownTicks`.

The scan interval is based on the reward cooldown and runs no faster than once per second. Reward and penalty cooldowns are tracked separately, so a player can be rewarded for a maintained village and later penalized if the same area no longer meets the requirements.

Config options:

- `welfare.enabled`: enables welfare scans.
- `welfare.scanRadius`: radius around each player to inspect.
- `welfare.minBeds`: required safe beds.
- `welfare.minJobSites`: required workstation blocks.
- `welfare.minSafeLight`: minimum light level for counted beds.
- `welfare.rewardEnabled`: enables positive gossip rewards.
- `welfare.rewardReputation`: amount of minor positive gossip added.
- `welfare.rewardCooldownTicks`: per-player reward cooldown.
- `welfare.penaltyEnabled`: enables poor-welfare penalties.
- `welfare.penaltyReputation`: amount of minor negative gossip added.
- `welfare.penaltyCooldownTicks`: per-player penalty cooldown.

## Commands

All commands require admin permission level.

```
/villageroverhaul help
/villageroverhaul reload
```

## Configuration

The config file is generated at:

```text
config/villager-overhaul.json
```

The file is loaded on server start and saved after command changes. Use `/villageroverhaul reload` to reload the file at runtime.

## Requirements

- Minecraft 26.1
- Fabric API
