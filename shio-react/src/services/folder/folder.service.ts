import type { ShFolder } from "@/models/folder/folder";
import axios from "axios";

export class ShFolderService {
  async get(id: string): Promise<ShFolder> {
    const response = await axios.get<ShFolder>(`/v2/folder/${id}`);
    return response.data;
  }

  async create(folder: Partial<ShFolder>, parentFolderId: string): Promise<ShFolder> {
    const response = await axios.post<ShFolder>(`/v2/folder/${parentFolderId}`, folder);
    return response.data;
  }

  async update(folder: ShFolder): Promise<ShFolder> {
    const response = await axios.put<ShFolder>(`/v2/folder/${folder.id}`, folder);
    return response.data;
  }

  async delete(id: string): Promise<void> {
    await axios.delete(`/v2/folder/${id}`);
  }

  async path(id: string): Promise<{ id: string; name: string; type: string }[]> {
    const response = await axios.get(`/v2/folder/${id}/path`);
    return response.data;
  }
}
