import type { ShPostType, ShPostTypeRenamed } from "@/models/post-type/post-type";
import axios from "axios";

/**
 * The post-type service — one name-keyed surface (`/api/v2/post-type`), shared
 * with the `shio` CLI and the agent path since SH117 retired the console's
 * id-keyed `/api/v2/post/type` twin.
 *
 * `create` and `update` are different verbs on purpose. `POST` conflicts on an
 * existing name, which is what the "new post-type" form needs — a collision there
 * is a mistake to report, not a content model to overwrite. `PUT` is the idempotent
 * upsert the CLI's `push` converges with. Both send `managed: false`: a console
 * save is a hand edit, and omitting the key would claim code ownership and make
 * the type read-only in its own editor.
 */
export class ShPostTypeService {
  async query(): Promise<ShPostType[]> {
    const response = await axios.get<ShPostType[]>("/v2/post-type");
    return response.data;
  }

  async get(name: string): Promise<ShPostType> {
    const response = await axios.get<ShPostType>(`/v2/post-type/${encodeURIComponent(name)}`);
    return response.data;
  }

  async create(postType: ShPostType): Promise<ShPostType> {
    const response = await axios.post<ShPostType>("/v2/post-type", {
      ...postType,
      managed: false,
    });
    return response.data;
  }

  async update(postType: ShPostType): Promise<ShPostType> {
    const response = await axios.put<ShPostType>(
      `/v2/post-type/${encodeURIComponent(postType.name)}`,
      { ...postType, managed: false }
    );
    return response.data;
  }

  /**
   * Rename a post-type, carrying its content with it (SH163).
   *
   * A `PUT` to a new name creates a *second* post-type and leaves this one's posts behind,
   * which is why the editor's API-name field was read-only. `managed: false` is the same hand-edit
   * claim every console write on this surface makes: the server refuses it against a code-managed
   * type instead of racing the repository that owns the model.
   */
  async rename(name: string, to: string): Promise<ShPostTypeRenamed> {
    const response = await axios.post<ShPostTypeRenamed>(
      `/v2/post-type/${encodeURIComponent(name)}/rename`,
      { to, managed: false }
    );
    return response.data;
  }

  async delete(name: string): Promise<void> {
    await axios.delete(`/v2/post-type/${encodeURIComponent(name)}`);
  }
}
