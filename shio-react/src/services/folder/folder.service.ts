import type { ShFolder } from "@/models/folder/folder";
import axios from "axios";

/** The CDA breadcrumb shape, shared by the console navigation reads (SH120). */
export interface ShCdaPath {
  site?: { id: string; name: string; description?: string; url?: string; rootFolderId?: string };
  breadcrumb: Array<{ id: string; name: string; root: boolean }>;
  currentFolder?: { id: string; name: string; root: boolean };
}

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

  /**
   * The folder's breadcrumb — `ShCdaPath` since SH120, the same shape
   * `/v2/object/{id}/path` and the CDA return. The old signature claimed a flat
   * `{id, name, type}[]`, which the endpoint never returned.
   */
  async path(id: string): Promise<ShCdaPath> {
    const response = await axios.get<ShCdaPath>(`/v2/folder/${id}/path`);
    return response.data;
  }
}
