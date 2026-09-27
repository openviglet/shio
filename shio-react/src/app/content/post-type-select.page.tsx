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
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { usePostTypes } from "@/hooks/use-catalogue"
import type { ShPostType } from "@/models/post-type/post-type"

/**
 * SH901 — choosing what to post, as tiles.
 *
 * Each post type is a **clickable choice**, so it is a `BentoTile` rather than a panel. *Create a
 * new Folder* and *Model a new Post Type* are choices like every other on this screen, so they are
 * tiles in their own section too, with a tone rather than a hardcoded hue (SH890).
 */
export default function BentoPostTypeSelectPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { folderId = "" } = useParams()
  // SH1046 — one cached read of the catalogue. `isPending` is the state the `finally` above was
  // maintaining by hand, and it is false on a failure too, which is what the `.catch()` meant.
  const { data, isPending: loading } = usePostTypes()
  const postTypes: ShPostType[] = data ?? []

  const systemTypes = postTypes.filter((pt) => pt.system)
  const customTypes = postTypes.filter((pt) => !pt.system)

  const select = (pt: ShPostType) => {
    // The content browser's New button reads this back; a curator posting twenty of one kind
    // should not scan the grid twenty times.
    localStorage.setItem("sh-last-post-type", pt.name)
    navigate(`${ROUTES.CONTENT_ROOT}/folder/${folderId}/post-type/${encodeURIComponent(pt.name)}/new`)
  }

  return (
    <div className="space-y-8">
      {/* The package's hero directly: this page has no save bar, so `PageHero` would add nothing. */}
      <BentoHero
        eyebrow={t("content.postLabel")}
        title={t("content.whatToPost")}
        backTo={`${ROUTES.BENTO_CONTENT}/object/${folderId}`}
        backLabel={t("content.browsing")}
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
    </div>
  )
}
