import { useEffect, useState } from "react";
import { useShioClient } from "../provider";
import type { ShPost } from "../types";

interface QueryOptions {
  /** Filter by site name (e.g. "viglet_corporate") */
  siteName?: string;
  /** Folder path within the site (e.g. "Home/Articles") */
  folderPath?: string;
}

export function useShioQuery(postTypeName: string | undefined, options?: QueryOptions) {
  const client = useShioClient();
  const [posts, setPosts] = useState<ShPost[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    if (!postTypeName) return;
    setLoading(true);

    const fetchPosts = options?.siteName
      ? client.queryBySite(options.siteName, postTypeName, options.folderPath)
      : client.getPostsByType(postTypeName);

    fetchPosts
      .then(setPosts)
      .catch(setError)
      .finally(() => setLoading(false));
  }, [client, postTypeName, options?.siteName, options?.folderPath]);

  return { posts, loading, error };
}
