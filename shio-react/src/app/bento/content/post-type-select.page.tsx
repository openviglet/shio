/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconFileText, IconFolderPlus, IconForms } from "@tabler/icons-react"
import { BentoHero, BentoSection, BentoTile } from "@viglet/viglet-design-system/bento"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import type { ShPostType } from "@/models/post-type/post-type"
import { ShPostTypeService } from "@/services/post-type/post-type.service"

const service = new ShPostTypeService()

/**
 * SH901 — choosing what to post, as tiles.
 *
 * ## Why this one is a copy where the editor is an adapter
 *
 * SH900's rule: a large working surface is migrated by adapter because what changes is its
 * chrome; a small page whose whole **structure** changes is worth writing twice. The post editor
 * is 375 lines of save/publish/preview/schedule handling wrapped around a widget renderer, and
 * only its header and panels differ between eras — adapter. This page is 142 lines, and its
 * `Card`s are not chrome at all: they are **clickable choices**, whose bento counterpart is
 * `BentoTile`. An adapter that turned a panel into a tile would be a different component, not a
 * chrome switch.
 *
 * ## The two "or" buttons became tiles as well
 *
 * The console page put *Or create a new Folder* and *Or model a new Post Type* in a header row,
 * one of them carrying a hardcoded orange (`text-orange-600 border-orange-300 …`) — one of the
 * seventeen files SH890 holds as accent debt. They are choices like every other choice on this
 * screen, so they are tiles in their own section, and the hue is a tone.
 */
export default function BentoPostTypeSelectPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { folderId = "" } = useParams()
  const [postTypes, setPostTypes] = useState<ShPostType[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    service.query()
      .then(setPostTypes)
      .catch(() => setPostTypes([]))
      .finally(() => setLoading(false))
  }, [])

  const systemTypes = postTypes.filter((pt) => pt.system)
  const customTypes = postTypes.filter((pt) => !pt.system)

  const select = (pt: ShPostType) => {
    // The console page remembers the last choice the same way; a curator posting twenty of one
    // kind should not scan the grid twenty times.
    localStorage.setItem("sh-last-post-type", pt.name)
    navigate(`${ROUTES.CONTENT_ROOT}/folder/${folderId}/post-type/${encodeURIComponent(pt.name)}/new`)
  }

  return (
    <main className="mx-auto max-w-7xl space-y-8 px-6 pb-12">
      {/*
        SH908 — `BentoHero`, not `PageHero`, and this was a crash rather than a style
        choice. This page lives in the bento tree and is mounted with **no**
        `SectionCardChromeProvider`, so the adapter answered with its console default: `PageHeader`
        → `SidebarTrigger` → `useSidebar()`, which throws where no `SidebarProvider` exists — and
        the bento shell ships none, by design. The route rendered a blank page behind the error
        boundary, with nothing in `tsc`, in the census or in any live probe to say so; it was found
        by `outside-shell-chrome.test.mjs` on its first run. A bento-only page has no second chrome
        to adapt to, so it names the hero it wants.
      */}
      <BentoHero
        eyebrow={t("content.postLabel")}
        title={t("content.whatToPost")}
        backTo={`${ROUTES.BENTO_CONTENT}/object/${folderId}`}
        leading={<IconFileText size={28} aria-hidden="true" />}
      />

      {!loading && customTypes.length > 0 && (
        <BentoSection title={t("content.postTypes")}>
          {customTypes.map((pt) => (
            <BentoTile
              key={pt.name}
              onClick={() => select(pt)}
              icon={IconFileText}
              tone="blue"
              eyebrow={t("content.postLabel")}
              title={pt.title || pt.name}
              span="col-span-2"
            >
              {pt.description && (
                <p className="text-muted-foreground text-xs">{pt.description}</p>
              )}
            </BentoTile>
          ))}
        </BentoSection>
      )}

      {!loading && systemTypes.length > 0 && (
        <BentoSection title={t("content.systemPostTypes")}>
          {systemTypes.map((pt) => (
            <BentoTile
              key={pt.name}
              onClick={() => select(pt)}
              icon={IconForms}
              tone="slate"
              eyebrow={t("content.postTypes")}
              title={pt.title || pt.name}
              span="col-span-2"
            />
          ))}
        </BentoSection>
      )}

      <BentoSection title={t("content.orInstead")}>
        <BentoTile
          to={`${ROUTES.BENTO_CONTENT}/folder/${folderId}/new-folder`}
          icon={IconFolderPlus}
          tone="amber"
          eyebrow={t("content.folder")}
          title={t("content.newFolder")}
          span="col-span-2"
        />
        <BentoTile
          to={ROUTES.POST_TYPE_NEW}
          icon={IconForms}
          tone="indigo"
          eyebrow={t("content.postTypes")}
          title={t("content.newPostType")}
          span="col-span-2"
        />
      </BentoSection>
    </main>
  )
}
