import type { ShWidget } from "@/models/widget/widget";
import axios from "axios";

export class ShWidgetService {
  async query(): Promise<ShWidget[]> {
    const response = await axios.get<ShWidget[]>("/v2/widget");
    return response.data;
  }
}
