import { useEffect, useState } from "react";
import { useShioClient } from "../provider";
import type { ShSite } from "../types";

export function useShioSites() {
  const client = useShioClient();
  const [sites, setSites] = useState<ShSite[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    setLoading(true);
    client
      .getSites()
      .then(setSites)
      .catch(setError)
      .finally(() => setLoading(false));
  }, [client]);

  return { sites, loading, error };
}
