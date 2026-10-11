# BuildAI prompt-to-blueprint compiler

**Core product:** natural-language building generation, not merely file conversion.

A model returns compact operations in JSON; ProceduralGenerator converts them into a format-neutral 3D voxel blueprint, which is passed to the same validation and construction engines as imported files.

Operations: fill, box, walls, shell, line; block and x1,y1,z1,x2,y2,z2. All integer coordinates and dimensions width,height,length. An air operation carves space. Later operations overwrite earlier ones. The compiler cannot run arbitrary code or Minecraft commands.

Authentication in this first development cut uses a local OpenAI API key. Sign in with ChatGPT requires a separate approved integration; do not imply a ChatGPT subscription automatically provides developer API access. No credentials are sent to game servers.
