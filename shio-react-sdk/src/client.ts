import type { ShioConfig, ShSite, ShObjectItem, ShFolderPath, ShPostRaw, ShPost, ShPostAttrRaw } from "./types";

/** Transform raw post attrs array into a key-value map */
function mapAttrs(attrs: ShPostAttrRaw[] | null): Record<string, string | string[] | null> {
  if (!attrs) return {};
  const result: Record<string, string | string[] | null> = {};
  for (const attr of attrs) {
    const name = attr.shPostTypeAttr.name;
    if (attr.arrayValue && attr.arrayValue.length > 0) {
      result[name] = attr.arrayValue;
    } else if (attr.dateValue) {
      result[name] = attr.dateValue;
    } else {
      result[name] = attr.strValue;
    }
  }
  return result;
}

/** Transform raw post to simplified post */
function toPost(raw: ShPostRaw): ShPost {
  return {
    id: raw.id,
    title: raw.title,
    summary: raw.summary,
    furl: raw.furl,
    date: raw.date,
    postType: raw.shPostType?.name ?? "",
    folderId: raw.shFolder?.id ?? "",
    attrs: mapAttrs(raw.shPostAttrs),
    rawAttrs: raw.shPostAttrs ?? [],
  };
}

/** Low-level Shio CMS HTTP client */
export class ShioClient {
  private baseUrl: string;
  private headers: Record<string, string>;

  constructor(config: ShioConfig) {
    this.baseUrl = config.baseUrl.replace(/\/$/, "");
    this.headers = { "Content-Type": "application/json" };
    if (config.apiToken) {
      this.headers["Key"] = config.apiToken;
    }
  }

  private async get<T>(path: string): Promise<T> {
    const res = await fetch(`${this.baseUrl}${path}`, {
      headers: this.headers,
      credentials: "include",
    });
    if (!res.ok) throw new Error(`Shio API ${res.status}: ${path}`);
    return res.json();
  }

  /** List all sites */
  async getSites(): Promise<ShSite[]> {
    return this.get<ShSite[]>("/v2/site");
  }

  /** Get a site by ID */
  async getSite(id: string): Promise<ShSite> {
    return this.get<ShSite>(`/v2/site/${id}`);
  }

  /** Find a site by name */
  async getSiteByName(name: string): Promise<ShSite | null> {
    const sites = await this.getSites();
    return sites.find((s) => s.name === name) ?? null;
  }

  /** Get root folders of a site */
  async getSiteRootFolders(siteId: string): Promise<ShObjectItem[]> {
    return this.get<ShObjectItem[]>(`/v2/site/${siteId}/folder`);
  }

  /** List children (folders + posts) of an object */
  async getChildren(objectId: string): Promise<ShObjectItem[]> {
    return this.get<ShObjectItem[]>(`/v2/object/${objectId}/list`);
  }

  /** List children filtered by post type */
  async getChildrenByType(objectId: string, postTypeName: string): Promise<ShObjectItem[]> {
    return this.get<ShObjectItem[]>(`/v2/object/${objectId}/list/${postTypeName}`);
  }

  /** Get breadcrumb/path for an object */
  async getPath(objectId: string): Promise<ShFolderPath> {
    return this.get<ShFolderPath>(`/v2/object/${objectId}/path`);
  }

  /** Get a post with all its attributes (simplified) */
  async getPost(postId: string): Promise<ShPost> {
    const raw = await this.get<ShPostRaw>(`/v2/post/${postId}`);
    return toPost(raw);
  }

  /** Get a raw post (full API response) */
  async getPostRaw(postId: string): Promise<ShPostRaw> {
    return this.get<ShPostRaw>(`/v2/post/${postId}`);
  }

  /** List posts by post type name (global, all sites) */
  async getPostsByType(postTypeName: string): Promise<ShPost[]> {
    const items = await this.get<ShPostRaw[]>(`/v2/post/post-type/${postTypeName}`);
    return items.map(toPost);
  }

  /**
   * Query posts by type within a specific site.
   * Navigates the site's folder tree and fetches full post data.
   *
   * @param siteName - Site name (e.g. "viglet_corporate")
   * @param postTypeName - Post type name (e.g. "Article", "Service")
   * @param folderPath - Optional folder path within the site (e.g. "Home/Articles")
   */
  async queryBySite(
    siteName: string,
    postTypeName: string,
    folderPath?: string
  ): Promise<ShPost[]> {
    const site = await this.getSiteByName(siteName);
    if (!site) return [];

    // Navigate to the target folder
    const folderId = await this.resolveFolderPath(site.id, folderPath);
    if (!folderId) return [];

    // List posts of the given type in the folder (and sub-folders)
    return this.getPostsInFolder(folderId, postTypeName);
  }

  /** Resolve a folder path like "Home/Articles" to a folder ID */
  private async resolveFolderPath(siteId: string, path?: string): Promise<string | null> {
    const rootFolders = await this.getSiteRootFolders(siteId);
    if (rootFolders.length === 0) return null;

    if (!path) return rootFolders[0].id;

    const segments = path.split("/");
    let currentFolder = rootFolders.find((f) => f.title === segments[0]);
    if (!currentFolder) return rootFolders[0].id;

    for (let i = 1; i < segments.length; i++) {
      const children = await this.getChildren(currentFolder.id);
      const child = children.find(
        (c) => c.objectType === "FOLDER" && c.title === segments[i]
      );
      if (!child) return currentFolder.id;
      currentFolder = child;
    }

    return currentFolder.id;
  }

  /** Get all posts of a type in a folder, fetching full attrs for each */
  private async getPostsInFolder(folderId: string, postTypeName: string): Promise<ShPost[]> {
    const children = await this.getChildren(folderId);

    const posts: ShPost[] = [];

    for (const child of children) {
      if (child.objectType === "POST" && child.shPostType?.name === postTypeName) {
        // Fetch full post with attrs
        const post = await this.getPost(child.id).catch(() => null);
        if (post) posts.push(post);
      } else if (child.objectType === "FOLDER") {
        // Recurse into sub-folders
        const subPosts = await this.getPostsInFolder(child.id, postTypeName);
        posts.push(...subPosts);
      }
    }

    return posts;
  }

  /** Get folder details */
  async getFolder(folderId: string): Promise<{ id: string; name: string }> {
    return this.get(`/v2/folder/${folderId}`);
  }
}
