import axios from "axios";
import type { ShRead } from "@/lib/read-options";

export type ShGroup = {
  id: string;
  name: string;
  description: string;
};

export class ShGroupService {
  async query(): Promise<ShGroup[]> {
    const response = await axios.get<ShGroup[]>("/v2/group");
    return response.data;
  }

  async get(id: string, read: ShRead = {}): Promise<ShGroup> {
    const response = await axios.get<ShGroup>(`/v2/group/${id}`, { signal: read.signal });
    return response.data;
  }

  async create(group: ShGroup): Promise<ShGroup> {
    const response = await axios.post<ShGroup>("/v2/group", group);
    return response.data;
  }

  async update(group: ShGroup): Promise<ShGroup> {
    const response = await axios.put<ShGroup>(`/v2/group/${group.id}`, group);
    return response.data;
  }

  async delete(id: string): Promise<void> {
    await axios.delete(`/v2/group/${id}`);
  }
}
