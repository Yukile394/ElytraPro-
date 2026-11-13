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

**Planks / logs**
- Right click while your body is inside a cobweb, planks or logs fires the rocket (`launchInsidePlanks`; lists: `insideBlockTags`, `insideBlocks`). Right-clicking a placed cobweb/planks/log also works.
- The old "fly through planks/logs" (phase) feature was REMOVED in 1.8.0: it caused rubber-banding/lag on servers.

**On/off key (v1.8.0)**
- Key (default `H`, Controls > ElytraPro) switches the whole mod on/off. Saved in `enabled`.

**Stage 1 (v1.7.0)**
- Key (default `G`, change it in Controls > ElytraPro) toggles it. Saved in `stage1Enabled`.
- ON + wings open + rocket click: if a player is within `stage1Range` (2 blocks) and inside the narrow `stage1Angle` cone in front of you, and a hotbar item has Knockback: swap to it, hit as a critical, swap back, all in one tick. Behind / beside = nothing. Wings closed = nothing.
- `stage1ReturnSlot` (1-9) is the slot it returns to (0 = the slot you were on). If the rocket is in your selected slot, it returns there so the rocket stays in hand.
- `debug: true` shows why a rocket click was ignored.

## Install
1. Install Fabric Loader 0.16.5+ and the Fabric API build for your version (1.21 or 1.21.1).
2. Put `elytrapro-<version>.jar` in `.minecraft/mods`.

## Build
```
gradle wrapper --gradle-version 8.10.2   # once, creates gradle-wrapper.jar
./gradlew build                        # Windows: gradlew.bat build
```
Output: `build/libs/elytrapro-1.8.0.jar`. Requires JDK 21.
GitHub Actions builds on every push and uploads the JAR as artifact `elytrapro-jar`.

## Config
`.minecraft/config/elytrapro.json` (created on first launch; old configs are reset once on update to v1.1.0):
`autoFirework`, `autoElytra`, `launchFromGround`, `cooldownTicks`, `skipIfBoosting`, `minAirTicks`, `onlyWhenFalling`, `launchInsideCobweb`, `triggerWithBlockInHand`, `triggerBlockTags`, `triggerBlocks`.

## Server rules
Automation mods may be forbidden on some servers. Check the rules before use.

## License
MIT
