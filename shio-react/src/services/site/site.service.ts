import type { ShSite } from "@/models/site/site";
import axios from "axios";

export class ShSiteService {
  async query(): Promise<ShSite[]> {
    const response = await axios.get<ShSite[]>("/v2/site");
    return response.data;
  }

  async get(id: string): Promise<ShSite> {
    const response = await axios.get<ShSite>(`/v2/site/${id}`);
    return response.data;
  }

  async create(site: ShSite): Promise<ShSite> {
    const response = await axios.post<ShSite>("/v2/site", site);
    return response.data;
  }

  async update(site: ShSite): Promise<ShSite> {
    const response = await axios.put<ShSite>(`/v2/site/${site.id}`, site);
    return response.data;
  }

  async delete(id: string): Promise<void> {
    await axios.delete(`/v2/site/${id}`);
  }

  async export(id: string): Promise<Blob> {
    const response = await axios.get(`/v2/site/${id}/export`, { responseType: "blob" });
    return response.data;
  }

  async import(file: File): Promise<void> {
    const formData = new FormData();
    formData.append("file", file);
    await axios.post("/v2/import", formData);
  }
}
