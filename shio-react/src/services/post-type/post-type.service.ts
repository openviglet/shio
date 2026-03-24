import type { ShPostType } from "@/models/post-type/post-type";
import axios from "axios";

export class ShPostTypeService {
  async query(): Promise<ShPostType[]> {
    const response = await axios.get<ShPostType[]>("/v2/post/type");
    return response.data;
  }

  async get(id: string): Promise<ShPostType> {
    const response = await axios.get<ShPostType>(`/v2/post/type/${id}`);
    return response.data;
  }

  async create(postType: ShPostType): Promise<ShPostType> {
    const response = await axios.post<ShPostType>("/v2/post/type", postType);
    return response.data;
  }

  async update(postType: ShPostType): Promise<ShPostType> {
    const response = await axios.put<ShPostType>(`/v2/post/type/${postType.id}`, postType);
    return response.data;
  }

  async delete(id: string): Promise<void> {
    await axios.delete(`/v2/post/type/${id}`);
  }
}
