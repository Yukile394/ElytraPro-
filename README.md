# ElytraPro

Client-side **Fabric** mod for **Minecraft 1.21 and 1.21.1**. Java 21.

## Features
**Auto Firework**
- Works ONLY while a firework rocket is in your main hand or off hand. Without a rocket in hand (or with any other item) nothing happens and everything stays vanilla. Rockets are never taken from the hotbar.
- With a rocket in hand, right-click an already **placed** log / planks / cobweb / bedrock block, or right-click anywhere while standing inside a cobweb (`launchInsideCobweb`): you take off with the rocket.
- Already gliding: rocket at once. In the air: wings open + rocket in the same tick. On the ground (`launchFromGround`): you jump, then wings open and the rocket fires.
- Never while sneaking, has a cooldown, skips if a rocket is still boosting you. A block in the main hand = vanilla placement (`triggerWithBlockInHand` changes that).

**Auto Elytra (cobweb only)**
- Press jump while standing inside a cobweb: Elytra opens ONE time per press (waits inside the same press until you are airborne). No retries, no packet spam, no automatic re-open.
- Everywhere else space is completely vanilla.
- The server can still close wings (ground contact, water, anti-cheat). The client cannot override that.

**Stage 1 (v1.11.0)**
- Key `G` (Controls > ElytraPro) toggles it. Saved in `stage1Enabled`.
- ON + a sword in the selected slot + a hit on a living entity: the hit uses the sword's damage and cooldown plus the Knockback enchantment of another item/block in your inventory. The selected slot never changes: the item is swapped into the sword's slot with inventory packets and back in the same tick.
- It always waits for the sword's full cooldown: an early click is queued for about 0.6 s and fires when the sword is ready and the crosshair is on a target.
- A sword that already has Knockback is left alone (normal hit).

**Fullbright (v1.9.0)**
- Client-side night vision, ON by default. Key `H` toggles it (Controls > ElytraPro), saved in `fullbright`.

## Install
1. Install Fabric Loader 0.16.5+ and the Fabric API build for your version (1.21 or 1.21.1).
2. Put `elytrapro-<version>.jar` in `.minecraft/mods`.

## Build
```
gradle wrapper --gradle-version 8.10.2   # once, creates gradle-wrapper.jar
./gradlew build                        # Windows: gradlew.bat build
```
Output: `build/libs/elytrapro-1.11.0.jar`. Requires JDK 21.
GitHub Actions builds on every push and uploads the JAR as artifact `elytrapro-jar`.

## Config
`.minecraft/config/elytrapro.json` (created on first launch; old configs are reset once on update to v1.1.0):
`autoFirework`, `autoElytra`, `launchFromGround`, `cooldownTicks`, `skipIfBoosting`, `minAirTicks`, `onlyWhenFalling`, `launchInsideCobweb`, `triggerWithBlockInHand`, `triggerBlockTags`, `triggerBlocks`, `fullbright`, `stage1Enabled`, `debug`.

## Server rules
Automation mods may be forbidden on some servers. Check the rules before use.

## License
MIT
