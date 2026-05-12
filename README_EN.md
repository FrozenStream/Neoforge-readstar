# ReadStar — NeoForge 26.1 Astronomy Mod

A Minecraft sky mod driven by real celestial mechanics and star catalog data, featuring planetary orbital systems, star catalogs, three-tier glow rendering, and server-synchronized planetary system configuration.

**Author**: FrozenStream

---

## Quick Start

```bash
# Build
./gradlew build

# Run client
./gradlew runClient

# Data generation (atlas config)
./gradlew runData
```

The celestial system is defined via **data pack** at `data/readstar/celestial/system.json`, and the star catalog is defined via **resource pack** at `assets/readstar/custom/stars/stars.json`.

After modifying config files, rebuild or use F3+T to reload resource packs (resource pack only; data packs require restart or `/reload`).

---

## Data Pack — Celestial System Configuration

**Path**: `data/readstar/celestial/system.json`

Place your custom data pack at `saves/<world>/datapacks/<your-pack>/data/readstar/celestial/system.json`, or on the server at `world/datapacks/`.

### Structure

```json
{
  "System": {
    "<body name>": {
      "mass": <double>,           // Mass (kg); 0 = fixed at origin
      "radius": <double>,         // Radius (m); affects rendered size
      "luminance": <int>,         // Self-luminosity (0~15); >0 marks as star
      "axis": [<x>, <y>, <z>],    // Rotation axis; zero vector defaults to (0,0,-1)
      "orbit": {
        "semiMajorAxis": <double>,           // Semi-major axis (m); 0 = no orbit
        "eccentricity": <double>,            // Eccentricity (0 = circular)
        "inclination": <double>,             // Orbital inclination (radians)
        "argumentOfPeriapsis": <double>,     // Argument of periapsis (radians)
        "longitudeOfAscendingNode": <double>, // Longitude of ascending node (radians)
        "initialMeanAnomaly": <double>       // Initial mean anomaly (radians)
      },
      "children": {
        "<child name>": { ... }   // Recursive; unlimited nesting depth
      }
    }
  }
}
```

### Complete Example

A three-layer system: Sun (central star) → Earth + Mars → Moon (Earth's satellite), using real solar system data.

```json
{
  "System": {
    "Sun": {
      "mass": 1.989e30,
      "radius": 6.957e8,
      "luminance": 15,
      "axis": [0.0, 0.0, 0.0],
      "orbit": {
        "semiMajorAxis": 0.0,
        "eccentricity": 0.0,
        "inclination": 0.0,
        "argumentOfPeriapsis": 0.0,
        "longitudeOfAscendingNode": 0.0,
        "initialMeanAnomaly": 0.0
      },
      "children": {
        "Earth": {
          "mass": 5.972e24,
          "radius": 6.371e6,
          "luminance": 0,
          "axis": [0.0, 0.0, 0.0],
          "orbit": {
            "semiMajorAxis": 1.496e11,
            "eccentricity": 0.0167,
            "inclination": 0.0,
            "argumentOfPeriapsis": 1.796,
            "longitudeOfAscendingNode": 0.0,
            "initialMeanAnomaly": 6.240
          },
          "children": {
            "Moon": {
              "mass": 7.342e22,
              "radius": 1.737e6,
              "luminance": 0,
              "axis": [0.0, 0.0, 0.5],
              "orbit": {
                "semiMajorAxis": 3.844e8,
                "eccentricity": 0.0549,
                "inclination": 0.0899,
                "argumentOfPeriapsis": 0.0,
                "longitudeOfAscendingNode": 0.0,
                "initialMeanAnomaly": 0.0
              },
              "children": {}
            }
          }
        },
        "Mars": {
          "mass": 6.417e23,
          "radius": 3.390e6,
          "luminance": 0,
          "axis": [0.0, 0.0, 0.0],
          "orbit": {
            "semiMajorAxis": 2.279e11,
            "eccentricity": 0.0934,
            "inclination": 0.0323,
            "argumentOfPeriapsis": 0.0,
            "longitudeOfAscendingNode": 0.865,
            "initialMeanAnomaly": 0.0
          },
          "children": {}
        }
      }
    }
  }
}
```

### Field Reference

| Field | Description |
|-------|-------------|
| `mass` | Core orbital mechanics parameter. A body with mass 0 is fixed at its parent's position |
| `radius` | Rendered size: `max(1.024, radius / distance × 2000)`. Diminishes with distance |
| `luminance` | Bodies with `luminance > 0` are recognized as stars; child bodies auto-assign the nearest luminous ancestor as `hostStar` |
| `inclination`, `longitudeOfAscendingNode` | Together define the orbital plane. `inclination=0` means the orbit lies in the reference plane |
| `eccentricity` | 0 = circular orbit, > 0 = elliptical |
| `initialMeanAnomaly` | Determines the body's starting position on its orbit at t=0 |

Names are case-insensitive (internally converted to lowercase). Use `{}` for empty children.

### Inheritance

- `hostStar` is resolved by recursively searching upward for the nearest body with `luminance > 0`
- Bodies without a luminous ancestor skip rendered sun-light effects
- Position: `position = parent.position + orbit(parent.mass, t)`
- Root node `Root` is fixed at `(0, 0, 0)`

### Daytime and Moon Phase

```
gameTime       → updatePositions(t)   → orbital revolution
daylightTime   → updateCurrentVec(t)  → rotation/zenith update
```

- `gameTime`: total ticks since world creation. Controls orbital positions
- `daylightTime`: dimension daylight cycle time (0~24000). Controls planet rotation and zenith direction

Moon phases are automatically computed from observer-satellite-star geometry, mapping a full orbit to all 8 phase types. Waxing vs. waning is determined by the sign of the observer-centric cross product against the orbital normal.

---

## Resource Pack — Star Catalog

**Path**: `assets/readstar/custom/stars/stars.json`

```json
{
  "Stars": [
    {
      "name": "Sirius",
      "position": [-0.188181, -0.169608, 0.967338],
      "Vmag": -1.46,
      "color": 4291815679
    }
  ]
}
```

| Field | Description |
|-------|-------------|
| `name` | Identifier; no runtime effect |
| `position` | Direction vector `[x,y,z]` on the unit sphere; normalized and scaled to 100 during rendering |
| `Vmag` | Apparent magnitude. `< 0.5`: high glow; `< 1.5`: medium glow; `< 2.0`: low glow; `≥ 2.0`: core only |
| `color` | ARGB color value; used as the key for atlas sprite generation |

Star brightness is mapped to vertex alpha: `clamp((14 - Vmag) / 15, 0.4, 1.0) × 255`.

---

## Resource Pack — Sprites & Atlas

### Star Atlas

The atlas source is registered via `RegisterSpriteSourcesEvent` with type `readstar:star`, declared in `assets/readstar/atlases/star.json`:

```json
{
  "sources": [ { "type": "readstar:star" } ]
}
```

At runtime, all `color` values from `stars.json` are read. The following base textures are used as stencils and tinted per-pixel:

| Stencil | Path | Purpose |
|---------|------|---------|
| `star_base.png` | `textures/environment/star/` | Core texture (32×32, RGBA) |
| `star_glow_low.png` | same | Low glow stencil |
| `star_glow_med.png` | same | Medium glow stencil |
| `star_glow_high.png` | same | High glow stencil |

Each color produces 4 sprites: `color_{color}`, `glow_low_{color}`, `glow_med_{color}`, `glow_high_{color}`.

Stencil requirements:
- RGBA 32-bit PNG (8-bit grayscale will fail `NativeImage.read()`)
- 32×32 pixels
- Pure black edges (`rgb(0,0,0)`) to blend into the background
- Glow brightness is suppressed by a factor of 0.35 based on the center 8×8 region sample

### Celestial Atlas

Generated by `runData`, output at `src/generated/resources/assets/readstar/atlases/celestial.json`. Add new celestial body textures by modifying `CelestialSpriteSourceProvider` and re-running data generation.

---

## Network Sync

The celestial system data is loaded from `data/readstar/celestial/*.json` on the server side and broadcast to all clients via the custom packet `readstar:planet_system`. `CelestialBodyManager.initializeFromJson()` parses and builds the body tree on the client upon receipt.

---

## Build

```bash
./gradlew build          # Build mod JAR
./gradlew runData        # Data generation (atlas config)
./gradlew runClient      # Run client
./gradlew runServer      # Run server
```

Output: `build/libs/readstar-*.jar`

### Troubleshooting

| Symptom | Check |
|---------|-------|
| Purple/black star textures | Is `star_base.png` 32-bit RGBA? |
| Sun/moon not visible | Is `Observer` properly assigned in `ExtractLevelRenderStateEvent`? |
| Celestial bodies in wrong direction | Check `currentRotationVector` calculation and `frameQuat` basis vectors |
| Data pack not applied | Run `/reload` or restart the server |
| Resource pack not updating | Press F3+T or rebuild |

---

© 2026 FrozenStream. Licensed under MIT.
