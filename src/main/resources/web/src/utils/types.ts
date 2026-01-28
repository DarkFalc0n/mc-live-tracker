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
