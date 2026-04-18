import axios from "axios";

export interface ShTenant {
  tenantId: string;
  multiTenant: boolean;
}

export class ShTenantService {
  async current(): Promise<ShTenant> {
    const response = await axios.get<ShTenant>("/v2/tenant/current");
    return response.data;
  }
}
