# MC Live Tracker (Fabric)

Starts an embedded web server on port `27134` when your Fabric server starts. It serves a simple web UI showing placeholder seed tiles and live player positions via SSE.

Future integration targets:
- World seed tiles from [Cubiomes](https://github.com/Cubitect/cubiomes)
- Rich map/UI inspiration from [Dynmap](https://github.com/webbukkit/dynmap)

## Features
- HTTP server on `http://localhost:27134`
- Static UI (`/`, `/app.js`, `/styles.css`)
- Live positions stream at `/events` (Server-Sent Events)
- Players JSON at `/api/players`
- Placeholder tile images at `/api/tile/{z}/{x}/{y}.png` (256x256)

## Build and install
Requires Java 17. If you don't have Gradle, install it (for example via SDKMAN) and optionally generate the Gradle wrapper for future builds.

1) Install Gradle (one-time):
   - Linux/macOS (SDKMAN):
     - `curl -s "https://get.sdkman.io" | bash`
     - `source "$HOME/.sdkman/bin/sdkman-init.sh"`
     - `sdk install gradle 8.10.2`

2) Build the mod jar:
   - Using system Gradle: `gradle build`
   - Or generate wrapper and then use it:
     - `gradle wrapper --gradle-version 8.10.2`
     - `./gradlew build`

3) Copy the jar from `build/libs/` to your Fabric server `mods/` folder.

4) Start the server and open `http://localhost:27134`.

## Configuration
- Port defaults to `27134`. Override via env var or JVM property:
   - `MCLIVETRACKER_PORT=3000`
   - `-Dmclivetracker.port=3000`

## Developing / extending
- Replace `TileRenderer.renderPlaceholderPng` with a renderer backed by Cubiomes. Expose world seed and dimension handling, cache tiles, and implement zoom/projection.
- Improve UI (panning/zooming, layers, markers). Consider a tile scheme like `z/x/y` in Web Mercator-like projection for overworld.

## License
MIT
