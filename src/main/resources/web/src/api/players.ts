import { PLAYERS_API_URL } from '../static/constants';
import type { Player } from '../utils/types';

export const getPlayers = async (): Promise<Player[]> => {
  const response = await fetch(PLAYERS_API_URL);
  if (!response.ok) {
    throw new Error('Failed to fetch players');
  }
  return response.json();
};
