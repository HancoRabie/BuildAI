# BuildAI Image-to-Pixel-Art Studio (Java 1.21.1)

BuildAI accepts **local image files** and converts them directly into Minecraft
block mosaics. This is an offline, deterministic operation: **no AI model
request, no remote upload and no image generation credits**.

## Player workflow

1. Press **F8 → Create / AI → Upload Image > Pixel Art**.
2. Select a local PNG, JPG or JPEG with **Browse Image**, or paste its full
   local path if the native system file dialog is unavailable.
3. Choose the maximum desired **width** (16 / 24 / 32 / 48 / 64 / 96 / 128
   blocks). Height automatically preserves the source aspect ratio; both
   dimensions are capped at 128 blocks.
4. Select **Concrete** or **Wool** (the standard 16 dye-color variants).
5. Select **Smooth** (photographs) or **Pixel** (logos and crisp pixel art)
   downsampling; retain PNG transparency as air, or fill it with white.
6. Click **Generate Preview**. The studio shows the approximate block-color
   mosaic plus the target dimensions, number of blocks and colors.
7. Click **Use Blueprint** to load it into the existing BuildAI
   **Placement / Export** review and preview workflow. BuildAI also
   saves a NEW uniquely named `.schem` to `buildai/blueprints/`.
   The original image is never changed or deleted.
8. Use **Upload & Preview** and the normal BuildAI confirmation workflow
   to place it in a test world, or export/import the saved schematic.

## Important behavior

- Source file maximum: 20 MB; source raster dimensions at most 4096×4096.
  Output at most 128×128, depth 1. Validates input before decoding.
- Portrait and landscape images keep their proportions. The width selector
  is an upper requested width; tall sources may be downscaled by height cap.
- Each visible pixel maps to the nearest available vanilla block color
  using weighted linear-light color distance. This is an approximation of
  Minecraft texture color under normal lighting, **not pixel-perfect RGB**.
- Transparent PNG pixels can become `minecraft:air`, forming silhouette
  openings. If transparency is disabled, those pixels become white.
  JPEG has no transparent pixels.
- The file browser is optional AWT native FileDialog (not Swing), run off
  the Minecraft render thread. On headless or unsupported setups players
  can paste a local file path instead.
- Material costs and 3D placement work exactly like other BuildAI blueprints.
  The image screen never places blocks in the world without the standard
  explicit placement confirmation.
- Images are not sent to the AI, to other clients or to a server during
  conversion. The resulting Minecraft schematic uses BuildAI's existing
  server blueprint upload workflow if the user initiates placement.
- Single-layer wall art only in this version. Depth sculpting, glow
  variants, map-art conversion and user-defined palettes are future options.
- This does not change redstone generation or invalidate an already
  running Redstone Benchy request.

## Relevant classes

- `src/main/java/com/hanco/buildai/blueprint/PixelArtConverter.java`
- `src/client/java/com/hanco/buildai/client/BuildAIPixelArtScreen.java`
- `src/test/java/com/hanco/buildai/blueprint/PixelArtConverterTest.java`

Test in a disposable world first. Depending on the selected scale and
materials, the construction can require thousands of colored blocks.
