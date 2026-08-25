// Numeric values mirror the MCVersion enum in third_party/cubiomes/biomes.h
const MC_VERSIONS: Record<string, number> = {
  '1.0': 3,
  '1.1': 4,
  '1.2': 5,
  '1.3': 6,
  '1.4': 7,
  '1.5': 8,
  '1.6': 9,
  '1.7': 10,
  '1.8': 11,
  '1.9': 12,
  '1.10': 13,
  '1.11': 14,
  '1.12': 15,
  '1.13': 16,
  '1.14': 17,
  '1.15': 18,
  '1.16': 20,
  '1.17': 21,
  '1.18': 22,
  '1.19': 24,
  '1.20': 25,
  '1.21': 28,
};

export const mcVersionToEnum = (version: string): number => {
  const match = version.match(/^(\d+)\.(\d+)/);
  if (!match) {
    return MC_VERSIONS['1.21'];
  }
  const key = `${match[1]}.${match[2]}`;
  return MC_VERSIONS[key] ?? MC_VERSIONS['1.21'];
};
