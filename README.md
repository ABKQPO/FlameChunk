# FlameChunk

FlameChunk is a Minecraft 1.7.10 Forge mod for server-side performance sampling, chunk heatmaps, and entity-load management. Sampling and administrative actions run on the server. Clients may install FlameChunk to view reports, live data, and map overlays.

## Installation and Compatibility

- Install FlameChunk on the server. In single-player, one installation provides both the integrated server and client features.
- Install FlameChunk on a client to open the diagnostics screen and use map integrations. JourneyMap 5, JourneyMap 6, Xaero's Minimap, and Xaero's World Map are optional dependencies.
- Navigator is optional. When installed, FlameChunk uses its map-layer API; otherwise, FlameChunk uses its built-in map adapters.
- ServerUtilities is optional. When installed, FlameChunk registers permissions for scans, weak-chunk cleanup, and loader controls, and can report claim information. Without it, player permissions are controlled by the server operator setting.
- JourneyMap 5 and 6 use different Forge mod IDs. Do not install both versions at the same time.

FTB/C2ME and Carpet integrations are not included in this port. Placer attribution and loader-region visualization depend on version capabilities unavailable in 1.7.10. Native Forge ticket sources, loader freezing, and available ServerUtilities claim information remain supported.

## Performance Analysis

The server produces bounded, on-demand reports for random ticks, scheduled ticks, block updates, block events, block entities, entities, natural spawning, event handlers, server tasks, and garbage collection. Chunk heatmaps use milliseconds per tick (MSPT). Totals do not count block-update time twice.

Start an analysis from the diagnostics screen, a supported map screen, or a command. Reports can appear in the diagnostics screen, chat, or both. When no compatible client is connected, reports can be read from the server console. Live subscriptions are controlled separately by server permissions and client settings. In single-player, a map screen that pauses the world also pauses server ticks. Close the map after starting a scan, or use the non-pausing diagnostics screen.

The diagnostics screen also provides independent performance observations, hotspot details, object hotspots, and stack details. When JFR stack sampling is unavailable, FlameChunk reports the degraded state while keeping the primary MSPT observations available.

## Maps and World Overlays

JourneyMap 5, JourneyMap 6, Xaero's World Map, and Xaero's Minimap can display performance heatmaps, weak-chunk risks, tooltips, and scan controls. Map context menus provide confirmed cleanup for a selected weak-chunk entity type and confirmed freeze controls for chunk loaders. When Navigator is installed, its layer API hosts the heatmap.

The in-world overlay can show nearby entity, item, and block-entity hotspots, plus beams for hotspot chunks. Targets are filtered by distance and view frustum; nearby item hotspots are clustered. Overlay updates pause when the world is paused.

## Commands

Durations are in seconds. Scan durations range from `1` to `86400`; omitting the duration uses the configured default.

| Command | Description |
| --- | --- |
| `/flamechunk scan [seconds]` | Start a performance scan. Available to the console, operators, and ServerUtilities users with permission. |
| `/flamechunk stop` | Stop the active scan and publish a partial report. |
| `/flamechunk report` | View the latest performance report. |
| `/flamechunk tickets` | List Forge ticket sources and saved frozen chunks. |
| `/flamechunk loader freeze <chunkX> <chunkZ> confirm` | Freeze active loader tickets in a chunk. |
| `/flamechunk loader unfreeze <chunkX> <chunkZ> confirm` | Restore loader tickets in a chunk. |
| `/flamechunk loader clear <chunkX> <chunkZ> confirm` | Clear active loader tickets in a chunk. |
| `/flamechunk loader frozen list` | List saved freeze policies. |
| `/flamechunk loader frozen clear-orphans` | Remove policies without active tickets in the current dimension. |
| `/flamechunk weakclear <chunkX> <chunkZ> <entityType> confirm` | Queue chunked cleanup for one entity type in a weak chunk. In-game players only. |

Entity cleanup permanently removes matching entities and cannot be undone. Targets are revalidated after confirmation and processed over multiple ticks.

By default, scans, loader controls, and weak-chunk cleanup require operator permission. With ServerUtilities installed, FlameChunk also checks the `flamechunk.scan`, `flamechunk.loadercontrol`, and `flamechunk.weakclear` permission nodes. Scan storage and packet sizes are bounded by configuration.

## Diagnostics and Management

- Weak-chunk diagnostics inspect entities in bounded batches, report dense chunks without ordinary chunk loading, and expose cleanup actions through maps and commands.
- Entity-load protection can remove excess entities when a chunk loads, using configurable thresholds, type limits, retention counts, inspection limits, and removal limits. It is disabled by default.
- Entity-load diagnostics periodically report high-entity chunks and available ServerUtilities claim information.
- Watchdog crash reports can include bounded summaries of Forge ticket sources, chunks, entities, and block entities. This diagnostic can be disabled.
- Loader-freeze policies are saved with world data and also apply to matching future tickets. Policies can be listed or cleared when orphaned.

Server and client settings are registered through GTNHLib `@Config` in the FlameChunk configuration directory. Client settings include heat thresholds and opacity, weak-chunk display, ticket markers, tooltip fields and categories, live subscriptions, world overlays, and report output. A key binding opens the diagnostics screen.

## Development

The project uses JDK 25 and RFG. On Windows, build without running unit tests with:

```powershell
.\gradlew.bat build -x test
```

Run the Java 25 server with:

```powershell
.\gradlew.bat runServer25
```

Each map client profile uses a separate run directory and inherits the launch arguments from `runClient25`:

| Gradle task | Additional map dependencies |
| --- | --- |
| `runClient25Jm5` | JourneyMap 5 |
| `runClient25Jm6` | JourneyMap 6 and its API |
| `runClient25Xaero` | Xaero's Minimap and Xaero's World Map |

The current Xaero development profile is affected by an installed `lwjgl3ify` compatibility issue: its `XaerosMinimapScrolling` Mixin targets an `onGuiClosed()V` method absent from the Xaero JAR in use. This can stop client startup before FlameChunk's integration initializes.
