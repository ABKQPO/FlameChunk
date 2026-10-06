# FlameChunk 1.7.10 Port Design

## Goal

FlameChunk provides server-side chunk performance sampling for Minecraft 1.7.10 Forge and exposes the result to optional client map integrations. The implementation combines the useful behavior found in MsptMap and ServerWarashi without copying ServerWarashi source code.

The server installation is mandatory. JourneyMap 5, JourneyMap 6, Xaero World Map, Xaero Minimap, and ServerUtilities are optional integrations. A client without a supported map mod can connect and run server scans, but no heatmap is rendered.

## Constraints

- Target Minecraft 1.7.10 Forge 10.13.4.1614 or newer 1.7.10-compatible Forge builds.
- Use the existing GTNH convention and UniMixin build setup.
- Do not add or run unit tests.
- Public classes are never package-private or `final`; record classes are not used because the target bytecode is Java 8.
- All class references use imports rather than fully qualified names.
- All source comments are written in English and do not use separator-style comment banners.
- Core code must not load optional map or ServerUtilities classes on a server without those mods.
- Data structures crossing the network are bounded and immutable after publication.
- All project files are read and written as UTF-8.

## Architecture

The project is split into four boundaries:

1. `common` contains tick categories, immutable snapshots, protocol constants, packet codecs, and shared configuration values. It depends only on Minecraft/Forge APIs and bundled libraries.
2. `server` owns sampling, aggregation, permission checks, commands, entity protection, weak-load inspection, loader policies, and optional ServerUtilities attribution.
3. `client` owns snapshot storage, color mapping, viewport clipping, tooltip data, and the standalone diagnostic state. It does not import map-mod classes.
4. `compat` contains one adapter per third-party API. JourneyMap 5 and 6 use separate source packages. Xaero integrations use client-only conditional Mixins because the supplied versions expose no stable public overlay API.

The server publishes a snapshot through a single network service. Map adapters consume a neutral `MapOverlayModel`; they never call the sampler directly. Optional integrations are registered from the client proxy only after the corresponding mod id is present. Conditional Mixin loading prevents Xaero target classes from being resolved when Xaero is absent.

## Stage 1: Core Sampling

Stage 1 replaces the example mod entry points with FlameChunk entry points and implements:

- Seven tick categories: random ticks, scheduled ticks, block updates, block events, block entities, entities, and mob spawning.
- Per-dimension, per-chunk aggregation with bounded storage and explicit reset at scan completion.
- Immutable chunk, dimension, and scan snapshots.
- Server command `/flamechunk scan [seconds]` with validated duration and operator permission.
- SimpleNetworkWrapper registration for scan request, progress, result, and clear messages.
- Protocol versioning, payload length limits, and a codec boundary for Zstd compression.
- Server configuration for scan duration, snapshot limits, permission policy, and entity guard defaults.
- English comments and UTF-8 resources, including English and Simplified Chinese language keys.

Stage 1 does not import JourneyMap, Xaero, or ServerUtilities and does not render a client heatmap.

## Stage 2: Client State

Stage 2 adds client snapshot storage, scan progress state, color calculation, chunk viewport clipping, and a standalone diagnostic screen. When no map mod is installed, the client only keeps the server scan path available; it does not render a heatmap.

## Stage 3: Map Integrations

JourneyMap 6 uses its `journeymap.api.v2` plugin and overlay/event APIs. JourneyMap 5 uses its `journeymap.client.api` API in a separate adapter. Xaero World Map and Minimap use conditional client Mixins targeting the supplied 1.7.10 GUI classes. Each adapter translates the neutral overlay model into its own coordinate, color, tooltip, button, and render lifecycle.

The build treats all map jars as compile-only or development-only inputs. They are never embedded in the FlameChunk artifact.

## Stage 4: Governance Integrations

Stage 4 adds entity load protection, weak-load scanning, chunk-loader freeze policies, watchdog diagnostics, and ServerUtilities claims/permission attribution. ServerUtilities access is isolated behind a small compatibility interface and is disabled when the mod or its expected API is unavailable.

## Dependency Policy

FastUtil and Zstd are ordinary library dependencies. If they must be embedded, the existing GTNH `shadowImplementation` mechanism remains responsible for packaging and relocation. JarJar is not a replacement for shadowing: it supplies 1.7.10 FML/RFB nested-jar discovery and loading. JourneyMap, Xaero, and ServerUtilities remain optional external mods.

## Error Handling

Malformed or oversized packets are rejected without disconnecting the client. A failed optional integration is logged once and disabled for the current client session. A failed dimension or chunk sample does not abort the entire scan; the affected entry is omitted and the scan continues. Snapshot publication happens only after all bounds are checked.

## Acceptance Criteria

- `gradlew.bat compileJava --no-daemon` succeeds without running test tasks.
- A dedicated server can start with FlameChunk and no map mod present.
- A client can connect to that server without JourneyMap or Xaero installed.
- `/flamechunk scan` validates duration and permission and produces a bounded result packet.
- Adding one optional map mod enables only its adapter; missing map mods do not resolve their classes.
- No third-party jar is modified, and no third-party source is copied into FlameChunk.
