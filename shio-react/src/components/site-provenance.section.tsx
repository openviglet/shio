/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconHistory } from "@tabler/icons-react"
import { useQuery } from "@tanstack/react-query"
import { BentoFormSection } from "@viglet/viglet-design-system/bento"
import { useTranslation } from "react-i18next"
import { addressKey } from "@/lib/query-keys"
import { ShSiteService } from "@/services/site/site.service"

const siteService = new ShSiteService()

/** The memory key `shio convert` records a replication round under (SH1135, `REPLICATION_NOTE_KEY`). */
export const REPLICATION_NOTE_KEY = "replication"

/**
 * SH1135 — where this site came from, for a curator opening one they did not build.
 *
 * <p>A replication round runs on somebody's machine and every artefact of it stays there; the only
 * part the instance hears about is the note `shio convert` leaves in the site's memory, which names
 * the source, the counts and the command that runs it again. This card reads that note and nothing
 * else. It is the console's half of "start a run" as well: the console cannot run a capture (the
 * instance does not run a browser), so it hands the curator the command the record already carries.</p>
 *
 * <p>It renders nothing for a site no round wrote into, and nothing while the read is in flight or
 * after it fails: an absent card makes no claim, where an empty one would say "not replicated".</p>
 */
export function SiteProvenanceSection({ siteName }: { siteName?: string }) {
  const { t } = useTranslation()
  const read = useQuery({
    queryKey: addressKey(`summary:provenance|${siteName ?? ""}`),
    queryFn: ({ signal }) => siteService.memory(siteName ?? "", { signal }),
    enabled: Boolean(siteName),
  })

  const note = read.data?.find((one) => one.key === REPLICATION_NOTE_KEY && one.scope === siteName)
  if (!note) return null

  return (
    <BentoFormSection icon={IconHistory} title={t("content.siteProvenance")}>
      <p className="text-sm">{note.note}</p>
      {(note.author || note.updated) && (
        <p className="text-xs text-muted-foreground">
          {t("content.siteProvenanceBy", {
            author: note.author ?? "—",
            date: note.updated ? new Date(note.updated).toLocaleDateString() : "—",
          })}
        </p>
      )}
    </BentoFormSection>
  )
}
