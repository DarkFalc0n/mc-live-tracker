import { SEED_API_URL } from "../static/constants";

export const getSeed = async (): Promise<{ seed: string }> => {
    const response = await fetch(SEED_API_URL);
    if (!response.ok) {
        throw new Error('Failed to fetch seed');
    }
    return response.json();
};