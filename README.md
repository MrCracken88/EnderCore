# End Core Portal

Fabric mod for Minecraft 26.2 (Java 25).

1. Craft an **EnderCore**: 8 Eyes of Ender around a Diamond.
2. Light a Nether portal and **drop** the EnderCore into it. The whole portal turns black.
3. Touch the black portal. The screen goes black and a sound plays, then you arrive in the End and
   your vision fades back in. You land on the vanilla obsidian platform with a return portal behind you.
4. Touch the return portal to come back out of the portal you left from. This time a faint green glow
   creeps in from the edges while you load, and recedes when you arrive.

Creative / testing: `/give @s end-core-portal:ender_core`

## Layout
- `ModItems`, `ModBlocks`, `ModAttachments`, `ReturnPoint`: registration and saved data
- `block/EndCorePortalBlock`: the black portal block (shape, breaks when the frame breaks)
- `logic/PortalConversion`: Nether portal -> black portal when an EnderCore is dropped in
- `logic/PortalTravel`: starting a trip, the countdown, End platform, return portal, trip back
- `net/PortalFxPayload`: tells the client when to start/stop the screen effect
- `ModSounds`: the two portal sounds (`assets/.../sounds/`)
- client `PortalFxClient`: draws the black fade / green glow and plays the sound
- `mixin/ItemEntityMixin`: hooks dropped items so the conversion can be detected

Timing: `TO_END_TICKS` / `TO_OVERWORLD_TICKS` in `PortalTravel` set how long until the teleport (20 ticks = 1 second;
they match the sound lengths). Look and feel (fade speeds, glow colour/strength) are the constants at the top of `PortalFxClient`.
