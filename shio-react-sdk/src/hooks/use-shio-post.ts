import { useEffect, useState } from "react";
import { useShioClient } from "../provider";
import type { ShPost } from "../types";

export function useShioPost(postId: string | undefined) {
  const client = useShioClient();
  const [post, setPost] = useState<ShPost | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    if (!postId) return;
    setLoading(true);
    client
      .getPost(postId)
      .then(setPost)
      .catch(setError)
      .finally(() => setLoading(false));
  }, [client, postId]);

  return { post, loading, error };
}
