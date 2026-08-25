import './App.css';
import Header from './components/Header';
import WorldMap from './components/WorldMap';
import { useEffect, useState } from 'react';
import PlayerModal from './components/PlayerModal';
import type { Player } from './utils/types';
import { getPlayers } from './api/players';

function App() {
  const [players, setPlayers] = useState<Player[]>([]);
  const [isModalOpen, setIsModalOpen] = useState(false);

  useEffect(() => {
    const fetchPlayers = async () => {
      try {
        const players = await getPlayers();
        setPlayers(players);
      } catch (error) {
        console.error('Error fetching players:', error);
      }
    };
    fetchPlayers();
  }, []);

  return (
    <div className="h-screen flex flex-col">
      <Header
        playerCount={players.length}
        onToggle={() => setIsModalOpen(!isModalOpen)}
      />
      <WorldMap />
      <PlayerModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        players={players}
      />
    </div>
  );
}

export default App;
