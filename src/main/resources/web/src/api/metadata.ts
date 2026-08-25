import { METADATA_API_URL } from '../static/constants';
import type { WorldMetadata } from '../utils/types';

export const getMetadata = async (): Promise<WorldMetadata> => {
  const response = await fetch(METADATA_API_URL);
  if (!response.ok) {
    throw new Error('Failed to fetch metadata');
  }
  return response.json();
};
