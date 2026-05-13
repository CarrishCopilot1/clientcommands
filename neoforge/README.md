# Enchantment Prediction (NeoForge 1.21.1)

A self-contained NeoForge 1.21.1 mod ported from the
[`clientcommands`](https://github.com/Earthcomputer/clientcommands) Fabric mod
by Earthcomputer. **Only the enchantment prediction & manipulation feature is
ported** — fishing manipulation, server-brand handling, the `/c…` swiss-army of
extra commands, etc. are intentionally out of scope.

## Commands (preserved from the original)

| Command                                                          | Behaviour |
|------------------------------------------------------------------|-----------|
| `/cenchant <item> [with <ench> <lvl/*>] [without <ench> <lvl/*>] [exactly] [--simulate]` | Search for and (unless `--simulate`) execute an item-throw plan that yields the requested enchantments. |
| `/ccrackrng`                                                     | Throw 10 dummy items and recover the player's 48-bit RNG seed via lattice reduction. |
| `/ctask list` / `/ctask stop <id>`                               | Inspect / cancel running long-tasks. |
| `/cconfig enchantpredict <key> [set <value>]`                    | Read / write a config key. Valid keys: `enchantingPrediction`, `playerRNGMaintenance`, `toolBreakWarning`, `maxEnchantItemThrows`, `minEnchantBookshelves`, `maxEnchantBookshelves`, `minEnchantLevels`, `maxEnchantLevels`, `maxEnchantSlot`, `itemThrowsPerTick`. |

The enchantment-table overlay (XP-seed status, bookshelf check, slot
predictions/clues, "Add Info" button) is rendered automatically when
`enchantingPrediction` is enabled.

## Building

```bash
cd neoforge
./gradlew build          # writes build/libs/enchantpredict-1.0.0.jar
./gradlew runClient      # launches a dev client
```

The build pulls Minecraft 1.21.1 + NeoForge 21.1.x via the `net.neoforged.moddev`
plugin, and shades the `seedfinding` / `latticg` libraries that the cracker
depends on. Mappings are Mojmap + Parchment 2024.11.17.

> **Sandbox note:** the CI in this repo runs without access to
> `maven.neoforged.net`, `maven.seedfinding.com`, `maven.latticg.com`, or
> `maven.parchmentmc.org`, so a clean `./gradlew build` cannot complete in this
> environment. Build locally to validate.

## Mapping vs. the Fabric original

Behaviourally identical wherever the 1.21.1 API permits. Notable adaptations:

* `clientcommands` targets MC 26.1.x with Java 25 and uses the post-1.21.2
  `Consumable` / `Equippable` / `ItemUseAnimation` data components and the
  1.21.5+ `GuiGraphicsExtractor` render-state API. All of those are rewritten
  against the 1.21.1 surface (`UseAnim`, `ArmorItem`, direct `GuiGraphics`
  drawing).
* `MultiVersionCompat` / `LegacyEnchantment` branches are dropped — the target
  is fixed to 1.21.1, so vanilla `EnchantmentTags.IN_ENCHANTING_TABLE` is used
  unconditionally.
* The Fabric command source (`FabricClientCommandSource`) is replaced by
  NeoForge's `CommandSourceStack`, supplied by `RegisterClientCommandsEvent`.
* Item-throw fence (originally backed by a brigadier suggestions round-trip) is
  approximated with a tick-based fence — adequate for client-side prediction.
* `/cenchant`'s `--simulate` flag is implemented with a small thread-local flag
  store rather than the Fabric `clientarguments` library.

## RNG event coverage (mixin set)

To stay surgical we ship only the four mixins that the prediction loop strictly
needs. The other RNG sources the original mod tracks (anvil, crossbow,
mending, frost-walker, etc.) just cause the cracker to reset rather than
silently desync — same contract as the original when running on a modded
server it doesn't recognise.

| Mixin                                | Purpose |
|--------------------------------------|---------|
| `enchant.EnchantmentScreenMixin`     | Render the overlay, add the "Add Info" button, hook the enchant button click. |
| `rngevents.ClientPacketListenerMixin`| Forward `ClientboundAddEntityPacket` to the cracker / item-throw task. |
| `rngevents.LocalPlayerMixin`         | Track keyboard item drops (Q / Ctrl+Q). |
| `rngevents.MultiPlayerGameModeMixin` | Capture the enchanting-table position on right-click. |

## License

MIT — same as the upstream `clientcommands` project.
