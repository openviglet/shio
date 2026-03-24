import axios from "axios";

export class ShAuthorizationService {
  async login(username: string, password: string): Promise<void> {
    const baseURL = axios.defaults.baseURL ?? "";
    const response = await fetch(`${baseURL}/v2`, {
      method: "GET",
      headers: {
        "Authorization": "Basic " + btoa(username + ":" + password),
        "X-Requested-With": "XMLHttpRequest",
      },
      credentials: "include",
    });
    if (!response.ok) {
      throw new Error("Invalid credentials");
    }
  }
}
