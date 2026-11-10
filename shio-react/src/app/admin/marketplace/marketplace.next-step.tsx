/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconExternalLink, IconFolder, IconPencil } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { Button } from "@/components/ui/button"
import { useSites } from "@/hooks/use-catalogue"
import { editorHref } from "@/lib/editor-path"
import { siteDestination } from "@/lib/site-front-end"

/**
 * SH1422 — what a site a package just created is for: looking at it and changing it.
 *
 * <p>The setup used to end on a toast and one link to the content root, the list of every site, so
 * the newcomer at their most engaged moment had to find the site they had just made. This reads the
 * setup's own answer (the site it applied to) and offers the three ways in: the Visual Editor first,
 * because editing in place is what a newcomer came to see, then a preview, then the page list.</p>
 *
 * <p>A preview and not the visitor's URL: an apply writes drafts (P7), so the published route has
 * nothing to show until somebody publishes, and a first link that answers 404 is the wrong first
 * impression. The site is found by name in the site catalogue, which the setup refreshes on its way
 * here; until it arrives the panel says the site is ready and points at the site list.</p>
 */
export function MarketplaceNextStep({ siteName }: Readonly<{ siteName: string }>) {
  const { t } = useTranslation()
  const { data: sites } = useSites()
  const site = sites?.find((each) => each.name === siteName)

  return (
    <div className="flex flex-col gap-2 rounded-md border border-primary/60 p-3 text-sm" data-testid="marketplace-next-step">
      <p className="font-medium">{t("marketplace.nextTitle", { site: siteName })}</p>
      <p className="text-muted-foreground">{t("marketplace.nextLead")}</p>
      {site?.id ? (
        <div className="flex flex-wrap gap-2">
          <Button asChild size="sm">
            <Link to={editorHref(ROUTES.VISUAL_EDITOR, site.id)} data-next-step="editor">
              <IconPencil size={16} aria-hidden="true" />
              {t("marketplace.nextEditor")}
            </Link>
          </Button>
          <Button asChild size="sm" variant="outline">
            <a href={siteDestination(site).url} target="_blank" rel="noreferrer" data-next-step="preview">
              <IconExternalLink size={16} aria-hidden="true" />
              {t("marketplace.nextPreview")}
            </a>
          </Button>
          <Button asChild size="sm" variant="outline">
            <Link to={`${ROUTES.CONSOLE}/content/object/${site.id}`} data-next-step="pages">
              <IconFolder size={16} aria-hidden="true" />
              {t("marketplace.nextPages")}
            </Link>
          </Button>
        </div>
      ) : (
        <Link className="underline" to={ROUTES.CONTENT_ROOT}>
          {t("marketplace.setupOpen")}
        </Link>
      )}
    </div>
  )
}
