import type { ShPost, ShPostAttr, ShRelatorItem } from "@/models/post/post";
import axios from "axios";

interface ShPostXP {
  shPost: ShPost;
  allowPublish: boolean;
}

/** Ensure Jackson @type discriminators are present for polymorphic entities */
function ensureJacksonTypes(post: ShPost | Partial<ShPost>): ShPost | Partial<ShPost> {
  if (!post.shPostAttrs) return post
  return {
    ...post,
    shPostAttrs: post.shPostAttrs.map(ensureAttrType),
  }
}

function ensureAttrType(attr: ShPostAttr): ShPostAttr {
  const typed: ShPostAttr = {
    ...attr,
    "@type": attr["@type"] || "ShPostAttr",
    // Empty string IDs cause Hibernate "multiple representations" errors — send null for new entities
    id: attr.id || (null as unknown as string),
  }
  if (typed.shChildrenRelatorItems && typed.shChildrenRelatorItems.length > 0) {
    typed.shChildrenRelatorItems = typed.shChildrenRelatorItems.map(ensureRelatorItemType)
  }
  return typed
}

function ensureRelatorItemType(item: ShRelatorItem): ShRelatorItem {
  return {
    ...item,
    "@type": item["@type"] || "ShRelatorItem",
    id: item.id || (null as unknown as string),
    shChildrenPostAttrs: item.shChildrenPostAttrs?.map(ensureAttrType) ?? [],
  }
}

export class ShPostService {
  async get(id: string): Promise<ShPostXP> {
    const response = await axios.get<ShPostXP>(`/v2/post/xp/${id}`);
    return response.data;
  }

  async create(post: Partial<ShPost>): Promise<ShPost> {
    const response = await axios.post<ShPost>("/v2/post", ensureJacksonTypes(post));
    return response.data;
  }

  async update(post: ShPost): Promise<ShPost> {
    const response = await axios.put<ShPost>(`/v2/post/${post.id}`, ensureJacksonTypes(post));
    return response.data;
  }

  async delete(id: string): Promise<void> {
    await axios.delete(`/v2/post/${id}`);
  }
}

export type { ShPostXP };
