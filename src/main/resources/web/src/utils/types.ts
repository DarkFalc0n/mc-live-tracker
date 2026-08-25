export type FCProps<T = unknown> = React.FC<
  {
    children?: React.ReactNode;
    className?: string;
  } & T
>;

export interface Player {
  name: string;
  x: number;
  y: number;
  z: number;
  yaw: number;
  pitch: number;
  dimension: string;
}

export interface WorldMetadata {
  minecraftVersion: string;
  modId: string;
  modVersion: string;
  levelName: string;
  seed: string;
  difficulty: string;
  gameMode: string;
  hardcore: boolean;
  dataVersion: number;
  dimensions: string[];
  spawn: {
    x: number;
    y: number;
    z: number;
    dimension: string;
  };
}
