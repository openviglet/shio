import type { ShCurrentUser } from "@/models/auth/current-user";
import type { ShUser } from "@/models/auth/user";
import axios from "axios";
import type { ShRead } from "@/lib/read-options";

export class ShUserService {
  async get(): Promise<ShCurrentUser> {
    const response = await axios.get<ShCurrentUser>("/v2/user/current");
    return response.data;
  }

  async list(): Promise<ShUser[]> {
    const response = await axios.get<ShUser[]>("/v2/user");
    return response.data;
  }

  async getByUsername(username: string, read: ShRead = {}): Promise<ShUser> {
    const response = await axios.get<ShUser>(`/v2/user/${username}`, { signal: read.signal });
    return response.data;
  }

  async create(user: ShUser): Promise<ShUser> {
    const response = await axios.post<ShUser>("/v2/user", user);
    return response.data;
  }

  async update(username: string, user: Partial<ShUser>): Promise<ShUser> {
    const response = await axios.put<ShUser>(`/v2/user/${username}`, user);
    return response.data;
  }

  async delete(username: string): Promise<void> {
    await axios.delete(`/v2/user/${username}`);
  }
}
