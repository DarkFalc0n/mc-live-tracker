import type { FCProps, Player } from '../utils/types';
import { cn } from '../utils/cn';
import { getSeed } from '../api/world';
import { useEffect, useState } from 'react';

interface PlayerModalProps {
  isOpen: boolean;
  onClose: () => void;
  players: Player[];
}

const PlayerModal: FCProps<PlayerModalProps> = ({
  className,
  isOpen,
  onClose,
  players,
}) => {
  const formatDimension = (dim: string) => {
    return dim
      .replace('minecraft:', '')
      .replace('_', ' ')
      .replace(/\b\w/g, (c) => c.toUpperCase());
  };

  const formatCoord = (coord: number) => Math.round(coord);
  const [seed, setSeed] = useState<string>('');

  useEffect(() => {
    async function getSeedData() {
      const seedData = await getSeed();
      setSeed(seedData.seed);
    }
    getSeedData();
  }, []);

  return (
    <>
      {/* Overlay */}
      <div
        className={cn(
          'fixed inset-0 top-16 bg-black/50 z-40 transition-opacity duration-300',
          isOpen ? 'opacity-100' : 'opacity-0 pointer-events-none'
        )}
        onClick={onClose}
      />

      {/* Slide-in Panel */}
      <div
        className={cn(
          'fixed top-16 right-0 h-full w-full max-w-md bg-zinc-900 shadow-xl z-50 transform transition-transform duration-300 ease-in-out p-6 overflow-y-auto',
          isOpen ? 'translate-x-0' : 'translate-x-full',
          className
        )}
      >
        <div className="flex flex-col justify-start items-start mb-6 gap-4">
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-white transition-colors cursor-pointer self-end"
          >
            <svg
              className="w-6 h-6"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M6 18L18 6M6 6l12 12"
              />
            </svg>
          </button>
          <h2 className="text-lg font-semibold text-white">
            Seed: <span className="text-white font-normal text-sm">{seed}</span>
          </h2>
          <h2 className="text-lg font-semibold text-white">Online Players</h2>
        </div>

        <div className="space-y-4">
          {players.length === 0 ? (
            <p className="text-gray-400 text-center">No players online.</p>
          ) : (
            players.map((player) => (
              <div
                key={player.name}
                className="bg-zinc-800 rounded-lg p-4 border border-zinc-700 shadow-sm"
              >
                <div className="flex justify-between items-start mb-2">
                  <h3 className="text-lg font-semibold text-white">{player.name}</h3>
                  <span className="text-xs font-mono bg-blue-900/50 text-blue-200 px-2 py-1 rounded">
                    {formatDimension(player.dimension)}
                  </span>
                </div>
                <div className="grid grid-cols-3 gap-2 text-sm text-gray-300">
                  <div className="bg-zinc-700/50 p-2 rounded text-center">
                    <span className="block text-xs text-gray-500 uppercase">X</span>
                    {formatCoord(player.x)}
                  </div>
                  <div className="bg-zinc-700/50 p-2 rounded text-center">
                    <span className="block text-xs text-gray-500 uppercase">Y</span>
                    {formatCoord(player.y)}
                  </div>
                  <div className="bg-zinc-700/50 p-2 rounded text-center">
                    <span className="block text-xs text-gray-500 uppercase">Z</span>
                    {formatCoord(player.z)}
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </>
  );
};

export default PlayerModal;
