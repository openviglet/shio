import axios from "axios";

export class ShSearchService {
  async search(query: string): Promise<unknown[]> {
    const response = await axios.get("/v2/search", { params: { q: query } });
    return response.data;
  }
}
