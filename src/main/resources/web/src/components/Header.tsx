import type { FCProps } from '../utils/types';
import { cn } from '../utils/cn';

const Header: FCProps<{ playerCount: number; onToggle: React.MouseEventHandler<HTMLButtonElement> }> = ({
  className,
  playerCount,
  onToggle,
}) => {
  return (
    <header
      className={cn(
        'h-16 flex items-center justify-between px-4 text-xl font-bold bg-zinc-800 z-50',
        className
      )}
    >
      <h1>Live Minecraft Tracker</h1>
      <div>
        <button
          onClick={onToggle}
          className="px-4 py-2 bg-zinc-700 rounded text-sm font-medium flex justify-start cursor-pointer active:bg-zinc-600 hover:bg-zinc-600 transition-colors"
        >
          <div className="flex items-center justify-center">
            <div className="h-2 w-2 bg-green-500 rounded-full mr-2"></div>
          </div>
          Connected - {playerCount}
        </button>
      </div>
    </header>
  );
};

export default Header;
