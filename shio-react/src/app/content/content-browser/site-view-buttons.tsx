/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { Eye, Globe } from "lucide-react"
import { useTranslation } from "react-i18next"
import { Button } from "@/components/ui/button"

/**
 * SH1304 — the hero's two ways of looking at a site: the curator's draft and the visitor's page.
 *
 * <p>"View Site" used to be the only one, and it opened `/preview/**`, so nothing in the browser
 * showed what a publish had produced. Both are secondaries (SH1195): neither is the page's primary.</p>
 */
export function SiteViewButtons({ onPreview, onPublished }: Readonly<{
  onPreview: () => void
  onPublished: () => void
}>) {
  const { t } = useTranslation()
  return (
    <>
      <Button variant="outline" size="sm" onClick={onPreview}>
        <Eye className="mr-2 h-4 w-4" /> {t("content.preview")}
      </Button>
      <Button variant="outline" size="sm" onClick={onPublished}>
        <Globe className="mr-2 h-4 w-4" /> {t("content.viewPublishedSite")}
      </Button>
    </>
  )
}
