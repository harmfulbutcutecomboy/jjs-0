# JJS Studio

Convert Roblox `.rbxl` / `.rbxm` / `.rbxlx` / `.rbxmx` ParticleEmitters, MeshParts, Cameras and Sounds into JJS skill JSON + `KLUv` import codes. Edit the live JSON in the Editor tab.

Parsing stays in the browser. Place files are never uploaded.

## Features

- Explorer for nested models / folders
- Particle conversion with flipbook / emit-count / spread sanitizing
- Mesh VISUAL nodes from MeshPart / SpecialMesh
- Camera VISUAL nodes sequenced with WAIT
- SFX nodes from Sound instances
- Beams show in the explorer only (JJS cannot play them)
- Pack mode, recolor, duration / position overrides
- Import editor: branch tabs, drag-swap, Default swap caches the original name
- Live JSON edits — zstd compresses only on Export / Copy
- Paste `KLUv/` or raw JSON

## Publish

1. Upload this folder to the **repo root**.
2. Pages → Deploy from branch → `/ (root)`.
3. Put `CircularSpotifyText-Black.otf` in `fonts/` if you have it.

## Editor notes

- Click a timeline node to inspect it. Click again to deselect (stats + add buttons return).
- Double-click a named branch tab to swap it with Default. Swap back restores the cached name.
- Drag one named tab onto another to swap their lines.
- Mesh inspector uses the Mesh field list. Camera inspector uses the Camera field list.
- Node icon URLs live on `window.JJS_NODE_ICONS` in `index.html`.
