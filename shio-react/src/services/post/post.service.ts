import type { ShPost } from "@/models/post/post";
import axios from "axios";

export class ShPostService {
  async get(id: string): Promise<ShPost> {
    const response = await axios.get<ShPost>(`/v2/post/${id}`);
    return response.data;
  }

  async create(post: Partial<ShPost>): Promise<ShPost> {
    const response = await axios.post<ShPost>("/v2/post", post);
    return response.data;
  }

  async update(post: ShPost): Promise<ShPost> {
    const response = await axios.put<ShPost>(`/v2/post/${post.id}`, post);
    return response.data;
  }

  async delete(id: string): Promise<void> {
    await axios.delete(`/v2/post/${id}`);
  }
}
