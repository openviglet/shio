import axios from "axios";

export type ShRole = {
  id: string;
  name: string;
  description: string;
};

export class ShRoleService {
  async query(): Promise<ShRole[]> {
    const response = await axios.get<ShRole[]>("/v2/role");
    return response.data;
  }

  async get(id: string): Promise<ShRole> {
    const response = await axios.get<ShRole>(`/v2/role/${id}`);
    return response.data;
  }

  async create(role: ShRole): Promise<ShRole> {
    const response = await axios.post<ShRole>("/v2/role", role);
    return response.data;
  }

  async update(role: ShRole): Promise<ShRole> {
    const response = await axios.put<ShRole>(`/v2/role/${role.id}`, role);
    return response.data;
  }

  async delete(id: string): Promise<void> {
    await axios.delete(`/v2/role/${id}`);
  }
}
