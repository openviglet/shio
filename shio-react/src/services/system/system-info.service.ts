import type { ShSystemInfo } from "@/models/system/system-info.model";
import axios from "axios";

export class ShSystemInfoService {
  async getInfo(): Promise<ShSystemInfo> {
    const response = await axios.get<ShSystemInfo>("/v2/system/info");
    return response.data;
  }
}
