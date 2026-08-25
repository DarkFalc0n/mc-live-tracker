import { useCallback, useEffect, useRef, useState } from 'react';
import type { MapgenModule } from '../wasm/mapgen.d';
import MapgenFactory from '../wasm/mapgen.js';
import { getMetadata } from '../api/metadata';
import { mcVersionToEnum } from '../utils/mcVersion';
import type { WorldMetadata } from '../utils/types';

interface View {
  cx: number;
  cz: number;
  bpp: number;
}

interface Tile {
  canvas: HTMLCanvasElement;
  worldX: number;
  worldZ: number;
  genScale: number;
}

const MIN_BPP = 0.5;
const MAX_BPP = 64;

const pickGenScale = (bpp: number): number => {
  if (bpp <= 4) return 4;
  if (bpp <= 16) return 16;
  return 64;
};

const clampBpp = (bpp: number): number =>
  Math.min(MAX_BPP, Math.max(MIN_BPP, bpp));

const WorldMap = () => {
  const containerRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const modRef = useRef<MapgenModule | null>(null);
  const metaRef = useRef<WorldMetadata | null>(null);
  const viewRef = useRef<View>({ cx: 0, cz: 0, bpp: 4 });
  const tileRef = useRef<Tile | null>(null);
  const regenTimerRef = useRef<number>(0);
  const draggingRef = useRef<{ x: number; y: number } | null>(null);
  const [status, setStatus] = useState<'loading' | 'waiting' | 'ready'>(
    'loading'
  );
  const [meta, setMeta] = useState<WorldMetadata | null>(null);

  const draw = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    const cw = canvas.width / dpr;
    const ch = canvas.height / dpr;
    const { cx, cz, bpp } = viewRef.current;

    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.fillStyle = '#101014';
    ctx.fillRect(0, 0, cw, ch);

    const tile = tileRef.current;
    if (tile) {
      const pxPerCell = tile.genScale / bpp;
      const screenX = cw / 2 + (tile.worldX - cx) / bpp;
      const screenY = ch / 2 + (tile.worldZ - cz) / bpp;
      const w = tile.canvas.width * pxPerCell;
      const h = tile.canvas.height * pxPerCell;
      ctx.imageSmoothingEnabled = false;
      ctx.drawImage(tile.canvas, screenX, screenY, w, h);
    }

    const meta = metaRef.current;
    if (meta?.spawn) {
      const sx = cw / 2 + (meta.spawn.x - cx) / bpp;
      const sy = ch / 2 + (meta.spawn.z - cz) / bpp;
      ctx.strokeStyle = '#ff5555';
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.arc(sx, sy, 6, 0, Math.PI * 2);
      ctx.stroke();
      ctx.beginPath();
      ctx.moveTo(sx - 10, sy);
      ctx.lineTo(sx + 10, sy);
      ctx.moveTo(sx, sy - 10);
      ctx.lineTo(sx, sy + 10);
      ctx.stroke();
    }
  }, []);

  const regenerate = useCallback(() => {
    const mod = modRef.current;
    const canvas = canvasRef.current;
    if (!mod || !canvas || !metaRef.current) return;

    const dpr = window.devicePixelRatio || 1;
    const cw = Math.ceil(canvas.width / dpr);
    const ch = Math.ceil(canvas.height / dpr);
    const { cx, cz, bpp } = viewRef.current;
    const genScale = pickGenScale(bpp);

    const halfW = (cw * bpp) / 2;
    const halfH = (ch * bpp) / 2;
    const worldX = Math.floor((cx - halfW) / genScale) * genScale;
    const worldZ = Math.floor((cz - halfH) / genScale) * genScale;
    const sx = Math.min(Math.ceil((halfW * 2) / genScale) + 2, 2048);
    const sz = Math.min(Math.ceil((halfH * 2) / genScale) + 2, 2048);

    const ptr = mod._mc_render(genScale, worldX, worldZ, sx, sz, 15);
    if (!ptr) return;

    const rgb = mod.HEAPU8.subarray(ptr, ptr + sx * sz * 3);
    const rgba = new Uint8ClampedArray(sx * sz * 4);
    for (let i = 0, j = 0; i < rgb.length; i += 3, j += 4) {
      rgba[j] = rgb[i];
      rgba[j + 1] = rgb[i + 1];
      rgba[j + 2] = rgb[i + 2];
      rgba[j + 3] = 255;
    }
    const tileCanvas = document.createElement('canvas');
    tileCanvas.width = sx;
    tileCanvas.height = sz;
    const tileCtx = tileCanvas.getContext('2d');
    if (!tileCtx) return;
    tileCtx.putImageData(new ImageData(rgba, sx, sz), 0, 0);
    tileRef.current = {
      canvas: tileCanvas,
      worldX,
      worldZ,
      genScale,
    };
    draw();
  }, [draw]);

  const scheduleRegenerate = useCallback(() => {
    window.clearTimeout(regenTimerRef.current);
    regenTimerRef.current = window.setTimeout(regenerate, 120);
  }, [regenerate]);

  useEffect(() => {
    let disposed = false;
    let pollTimer = 0;

    const loadMeta = async () => {
      try {
        const meta = await getMetadata();
        if (!meta || !meta.seed) return false;
        const mod =
          modRef.current ??
          (modRef.current = ((await MapgenFactory()) as MapgenModule));
        if (disposed) return true;
        mod._mc_init(mcVersionToEnum(meta.minecraftVersion), BigInt(meta.seed));
        metaRef.current = meta;
        setMeta(meta);
        viewRef.current.cx = meta.spawn?.x ?? 0;
        viewRef.current.cz = meta.spawn?.z ?? 0;
        setStatus('ready');
        regenerate();
        return true;
      } catch {
        return false;
      }
    };

    void loadMeta().then((ok) => {
      if (!ok && !disposed) {
        setStatus('waiting');
        pollTimer = window.setInterval(async () => {
          if (await loadMeta()) {
            window.clearInterval(pollTimer);
          }
        }, 2000);
      }
    });

    const canvas = canvasRef.current!;
    const container = containerRef.current!;

    const resize = () => {
      const dpr = window.devicePixelRatio || 1;
      canvas.width = container.clientWidth * dpr;
      canvas.height = container.clientHeight * dpr;
      canvas.style.width = `${container.clientWidth}px`;
      canvas.style.height = `${container.clientHeight}px`;
      draw();
      scheduleRegenerate();
    };
    resize();

    const observer = new ResizeObserver(resize);
    observer.observe(container);

    const onPointerDown = (e: PointerEvent) => {
      draggingRef.current = { x: e.clientX, y: e.clientY };
      canvas.setPointerCapture(e.pointerId);
    };
    const onPointerMove = (e: PointerEvent) => {
      const drag = draggingRef.current;
      if (!drag) return;
      const { bpp } = viewRef.current;
      viewRef.current.cx -= (e.clientX - drag.x) * bpp;
      viewRef.current.cz -= (e.clientY - drag.y) * bpp;
      draggingRef.current = { x: e.clientX, y: e.clientY };
      draw();
      scheduleRegenerate();
    };
    const onPointerUp = (e: PointerEvent) => {
      draggingRef.current = null;
      canvas.releasePointerCapture(e.pointerId);
    };
    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      const rect = canvas.getBoundingClientRect();
      const mx = e.clientX - rect.left;
      const my = e.clientY - rect.top;
      const prev = viewRef.current;
      const nextBpp = clampBpp(prev.bpp * Math.exp(e.deltaY * 0.0015));
      if (nextBpp === prev.bpp) return;
      const wx = prev.cx + (mx - rect.width / 2) * prev.bpp;
      const wz = prev.cz + (my - rect.height / 2) * prev.bpp;
      viewRef.current = {
        cx: wx - (mx - rect.width / 2) * nextBpp,
        cz: wz - (my - rect.height / 2) * nextBpp,
        bpp: nextBpp,
      };
      draw();
      scheduleRegenerate();
    };

    canvas.addEventListener('pointerdown', onPointerDown);
    canvas.addEventListener('pointermove', onPointerMove);
    canvas.addEventListener('pointerup', onPointerUp);
    canvas.addEventListener('wheel', onWheel, { passive: false });

    return () => {
      disposed = true;
      window.clearInterval(pollTimer);
      window.clearTimeout(regenTimerRef.current);
      observer.disconnect();
      canvas.removeEventListener('pointerdown', onPointerDown);
      canvas.removeEventListener('pointermove', onPointerMove);
      canvas.removeEventListener('pointerup', onPointerUp);
      canvas.removeEventListener('wheel', onWheel);
    };
  }, [draw, regenerate, scheduleRegenerate]);

  return (
    <div ref={containerRef} className="relative flex-1 overflow-hidden">
      <canvas ref={canvasRef} className="block cursor-grab active:cursor-grabbing" />
      {status !== 'ready' && (
        <div className="absolute inset-0 flex items-center justify-center bg-zinc-900/80 text-zinc-300 text-lg">
          {status === 'loading' ? 'Loading map engine…' : 'Waiting for a Minecraft world…'}
        </div>
      )}
      {status === 'ready' && meta && (
        <div className="absolute bottom-2 left-2 rounded bg-zinc-800/80 px-3 py-1.5 text-xs font-medium text-zinc-300 pointer-events-none">
          Seed: {meta.seed} · MC {meta.minecraftVersion}
        </div>
      )}
    </div>
  );
};

export default WorldMap;
