import { useEffect, useState } from "react";
import { useShioClient } from "../provider";
import type { ShObjectItem } from "../types";

export function useShioChildren(objectId: string | undefined) {
  const client = useShioClient();
  const [items, setItems] = useState<ShObjectItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    if (!objectId) return;
    setLoading(true);
    client
      .getChildren(objectId)
      .then(setItems)
      .catch(setError)
      .finally(() => setLoading(false));
  }, [client, objectId]);

  return { items, loading, error };
}
