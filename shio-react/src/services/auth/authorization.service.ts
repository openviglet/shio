import type { ShDiscoveryAPI } from "@/models/auth/discovery";
import type { ShCurrentUser } from "@/models/auth/current-user";
import type { ShRead } from "@/lib/read-options";
import axios from "axios";

export class ShAuthorizationService {
  async login(username: string, password: string): Promise<ShCurrentUser> {
    const baseURL = axios.defaults.baseURL ?? "";
    const response = await fetch(`${baseURL}/v2/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ username, password }),
    });
    if (!response.ok) {
      throw new Error("Invalid credentials");
    }
    return response.json() as Promise<ShCurrentUser>;
  }

  async discovery(read: ShRead = {}): Promise<ShDiscoveryAPI> {
    const response = await axios.create().get<ShDiscoveryAPI>("/v2/discovery", { signal: read.signal });
    return response.data;
  }
}
