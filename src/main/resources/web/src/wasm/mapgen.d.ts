export interface MapgenModule {
  _mc_init(mc: number, seed: bigint): number;
  _mc_render(
    scale: number,
    x: number,
    z: number,
    sx: number,
    sz: number,
    y: number
  ): number;
  _mc_biome_at(scale: number, x: number, y: number, z: number): number;
  HEAPU8: Uint8Array;
}

declare function MapgenFactory(): Promise<MapgenModule>;

export default MapgenFactory;
