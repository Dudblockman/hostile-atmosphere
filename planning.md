# Hostile Atmosphere — Planning & Architecture

## Project Structure
Multiloader mod (NeoForge 1.21.1) using the multiloader template.
- `common/` — platform-agnostic logic: engine, data, network payloads, progression, commands
- `neoforge/` — platform-specific wiring: events, config, registry, mixins, compat

## Core Systems

### Zone System
Zones are data-pack driven, loaded from `data/<ns>/zones/<id>.json`.

**`ZoneDefinition`** (common) fields:
- `dimension` — `ResourceLocation`, default `minecraft:overworld`. Zones are scoped per-dimension.
- `ceiling` — `List<CeilingLayer>` pipeline of `{operation, source}` steps. Same add/cap/floor model as the runtime modifier command. `evalCeiling(tick, x, z)` evaluates this at runtime.
- `hazardTimeSecs` — seconds to drain full air in this zone
- `toxinBuildupSecs` — seconds to accumulate max toxin in this zone

**CeilingLayer operations** (mirror of `AtmosphereModifier.Operation`):
- `add` — accumulates value
- `cap` (CLAMP_MAX) — limits maximum
- `floor` (CLAMP_MIN) — enforces minimum

**ValueSource types** for ceiling (and modifier) pipelines:
- `constant` — fixed value with optional tween ramp (`tweenTicks`, `startTick`)
- `sin` — sine wave (`amplitude`, `period`, `phase`, optional tween)
- `perlin` — spatially-varying Perlin noise (`xzScale`, `amplitude`, `timeTicks`, `seed`, optional tween)

**Example zone JSON:**
```json
{
  "dimension": "minecraft:overworld",
  "ceiling": [
    {"operation": "add", "source": {"type": "constant", "value": 66}},
    {"operation": "floor", "source": {"type": "constant", "value": 0}}
  ],
  "hazardTimeSecs": 480,
  "toxinBuildupSecs": 2400
}
```

**Effective ceiling** = `zone.evalCeiling(tick, x, z)` + `progression.getLevelForZone(tick, x, z, zoneId)`
- Data-pack ceiling pipeline: designer-time baseline, can be animated (sin, perlin)
- Progression level: runtime adjustment via `/atmosphere modifier` commands, stored in world save

### Zone Cache (`AtmosphereEventHandler`)
- `zonesByDim: Map<ResourceLocation, List<ZoneDefinition>>` — keyed by dimension, sorted ascending by `evalCeiling(0,0,0)` (lowest ceiling = most severe first)
- `zoneIdsByDim: Map<ResourceLocation, List<String>>` — parallel ID lists
- Rebuilt on `ServerStartedEvent` and `OnDatapackSyncEvent`
- Helpers: `dimZones(dim)`, `dimIds(dim)`

### Zone Lookup
**`AtmosphereEventHandler.findZoneAt(Level, x, y, z)`** — dual-sided utility:
- `ServerLevel` → full Perlin progression data (`findZoneAt(ServerLevel, x, y, z)`)
- `ClientLevel` → base ceiling pipeline from data-pack registry only (no progression offset)
- Always takes world-space coordinates; Sable sub-level callers transform local→world first

**`AtmosphereEngine.findZone`** (common) — pure lookup:
- `findZone(zones, tick, x, eyeY, z, atmosphereLevel)` — global offset variant
- `findZone(zones, zoneIds, tick, x, eyeY, z, levelForZone)` — per-zone offset variant

### Progression / Modifier System (`AtmosphereProgressionData`)
Stored in world save (`SavedData`). `TreeMap<Integer, AtmosphereModifier>` ordered by key.
- `getLevelForZone(tick, x, z, zoneId)` — evaluates "all" modifiers + zone-specific ones in key order
- `ensureKey0()` — seeds a no-op constant at key 0 on zone load
- Wired via `/atmosphere modifier add/remove/list/clear` commands (`ModifierCommand`)

### Player Hazard Engine (`AtmosphereEngine.tick`)
Per-tick, server-side only:
1. Grace period check (new players get N in-game days of immunity, config-driven)
2. Air debt drain/recovery via attribute-modified rates
3. Miasma damage ramp (3 tiers: t2Secs, t3Secs, each with damage + interval)
4. Toxin buildup/recovery
5. `AtmosphericToxicity` MobEffect application (4 amplifier levels, thresholds config-driven)

Attributes: `air_drain_rate`, `toxin_rate` (both on players, syncable, range 0–4, default 1.0).

### Protection Levels (Create compat)
`ProtectionLevel` enum (common): `NONE`, `RESPIRATOR`, `SEALED`
- `SEALED` (full Netherite Diving Suit + backtank): no air drain, no toxin
- `RESPIRATOR` (any diving helmet + backtank): air drain stopped, toxin seeps at `expeditionToxinMultiplier`
- `NONE`: no protection — bare backtank without a diving helmet gives no benefit

`CreateCompat` (neoforge) uses the inner `Delegate` class pattern — all Create class refs inside `Delegate`, which the JVM only loads when Create is present.

### Sable Sub-Level Compat
`SableCompat.getWorldSpacePos(BlockEntity)` — converts local sub-level coordinates to world-space using `SableCompanion.INSTANCE.getContaining(be).logicalPose().transformPosition(...)`. `sable-companion` is `compileOnly` (bundled as JarJar inside Sable's jar, no `localRuntime` needed).

**`BacktankBlockEntityMixin`** (`@Pseudo`, `defaultRequire: 1`):
- Injects before `airLevelTimer` GETFIELD (ordinal 0) in `BacktankBlockEntity.tick()`
- Resolves world-space position (Sable transform if loaded, block pos otherwise)
- Calls `findZoneAt(level, wx, wy, wz)` — cancels on both server (fill) and client (particles)

### Network Payloads (common, all server→client)
- `SyncAirDebtPayload` — int airDebt
- `SyncToxinPayload` — int toxinLevel, int miningFatigueAmp
- `SyncDivingActivePayload` — boolean divingActive
- `SyncZoneSeverityPayload` — float hazardIntensity, boolean approaching, int zoneCeilingY, int zoneFloorY

`AtmosphereClientData` — static maps (UUID→value) for client-side state. Updated by packet handlers in `ModNetworking`.

`hazardIntensity` = `leastSevereTimeSecs / thisZoneTimeSecs` so particle density scales automatically from data-pack zone values. No hardcoded per-zone values on client.

### Commands
`/atmosphere status [player]` — shows zone, air debt, toxin, rates, protection, effective ceiling
`/atmosphere reset [player]` — zeroes all player data
`/atmosphere setairdebt/settoxin/setgrace` — debug setters
`/atmosphere config` — dumps config values
`/atmosphere modifier add/remove/list/clear` — runtime ceiling modifier pipeline

`DebugCommands` (common) takes lambdas for all platform-specific data access (injected by `DebugCommandsHandler`). `effectiveCeilingGetter` returns `zone.evalCeiling(tick,x,z) + progressionLevel` for the player's current zone.

### Config (`AtmosphereConfig`, neoforge server config)
Key values: `safeZoneRecoverySecs`, `gracePeriodDays`, miasma ramp (tier2/3 secs + damage + intervals), toxin thresholds (4 levels), `toxinDeathCap`, underwater multipliers, conduit purification, expedition toxin multiplier.

Snapshot stored as `AtmosphereSettings` record (common) — passed to engine, avoids repeated config reads per tick.

### Tests (NeoForge GameTests)
- `ZoneLookupTests` — `AtmosphereEngine.findZone` with constant-ceiling zones
- `ModifierComputationTests` — `AtmosphereProgressionData.computeLevel` pipeline logic
- `ToxinAmplifierTests` — `AtmosphereEngine.getToxinAmplifier` threshold checks

Test zones use `ZoneDefinition.ofConstant(dim, yCeiling, hazardTimeSecs, toxinBuildupSecs)` factory. `findZone` calls pass `0L, 0.0, eyeY, 0.0` for tick/x/z (constant ceilings are position-independent).

## Key Design Decisions

- **Two-tier ceiling system**: data-pack pipeline (`ZoneDefinition.ceiling`) provides the animated baseline; runtime modifier commands (`AtmosphereProgressionData`) add on top. Effective = `evalCeiling(tick,x,z) + getLevelForZone(tick,x,z,zoneId)`.
- **Dimension scoping**: zones carry a `dimension` field; cache is `Map<ResourceLocation, List<ZoneDefinition>>`. Cross-dimension play naturally gets different hazard profiles.
- **No hardcoded zone names/counts**: particle intensity, toxin thresholds, and sync all derive from data-pack values.
- **Sort order**: zones sorted ascending by `evalCeiling(0,0,0)` at cache-rebuild time. Designers should ensure zone ceilings don't cross (no guarantee at runtime with animated ceilings).
- **Client-side zone check**: uses data-pack registry stream + `evalCeiling(level.getGameTime(), x, z)` — no progression offset, but ceiling animation (sin/perlin) is visible client-side.
- **Soft deps (Create, Sable)**: both use inner `Delegate` / method-body-only class-load patterns so the outer class is safe to load without the optional mod.

## Milestones

### 0.5 — Dimension-aware zones + ceiling pipeline
- `ZoneDefinition` gained `dimension: ResourceLocation` (default `minecraft:overworld`); zones are now scoped per-dimension
- Replaced `int yCeiling` with `List<CeilingLayer>` pipeline — same `add`/`cap`/`floor` + `constant`/`sin`/`perlin` model as the modifier command system; `evalCeiling(tick, x, z)` evaluates at runtime
- `ZoneDefinition.ofConstant(dim, yCeiling, hazardTimeSecs, toxinBuildupSecs)` factory added for tests and simple zones
- Zone cache upgraded from flat lists to `Map<ResourceLocation, List<ZoneDefinition>>` keyed by dimension; helpers `dimZones(dim)` / `dimIds(dim)` added to `AtmosphereEventHandler`
- `AtmosphereEngine.findZone` overloads updated to take `long tick, double x, double z` for pipeline evaluation
- `AtmosphereEventHandler.findZoneAt` is dual-sided: client path evaluates pipeline from registry stream with `level.getGameTime()`; server path goes through full progression data
- `getEffectiveCeiling(ServerLevel, ZoneDefinition, x, z)` added for debug command display
- Default zone ceilings tuned: lethal ≤ −16, toxic ≤ 30, hazy ≤ 66 (1.21 bedrock is y=−64; lethal floor at −16 still leaves hazard space)
- Zone JSON format updated to include `dimension` + `ceiling` pipeline array; all three shipped zones updated
- `ZoneLookupTests` rewritten for new values and API signatures; `ofConstant` factory used throughout
- Code simplification: BacktankMixin Sable branch collapsed to single position resolver, `ModNetworking` redundant same-package imports removed, `DebugCommandsHandler` replaced duplicated lookup with `findZoneAt`/`getEffectiveCeiling`, stale Javadoc removed from `ClientEventHandler`

**Known issue:** `runGameTestServer` crashes when Sable is present in `localRuntime` — Sable's Rapier JNI native (`Rapier3D.initialize`) fails to link in the headless game test server. A `localRuntime` configuration split was rejected; fix is pending (options: Sable system property to disable physics, JVM native-lib path config, or pre-extraction task).

## Pending / Future Work

- **Sable game test fix** — resolve `UnsatisfiedLinkError` for Rapier native in `runGameTestServer` without splitting `localRuntime`
- **CompatTests** — game test class asserting `SableCompat.isLoaded()` and `CreateCompat.isLoaded()` reflect the test environment correctly, as a smoke check for optional-dep detection
- Fog rendering / client-side visual overlay (partially wired via `hazardIntensity` / `zoneCeilingY` sync)
- Item tags for goggles (protection detection without hard Create dep)
- Client config (particle density toggle, etc.)
- Curios detection for accessories
- Fabric platform implementation (structure exists, no event handlers yet)
