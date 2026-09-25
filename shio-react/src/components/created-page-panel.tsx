/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { Eye } from "lucide-react"
import { Button } from "@/components/ui/button"
import { reportProblem } from "@/lib/problem"
import { ShInsightsService, type ShPageDigest } from "@/services/insights/insights.service"
import { ShPreviewTokenService } from "@/services/preview-token/preview-token.service"

const insightsService = new ShInsightsService()
const previewService = new ShPreviewTokenService()

/**
 * SH988 — what a created page is, beside a diff that has no "before" to compare against.
 *
 * <p>A create's diff is every field as an addition, which says what was written and not what it
 * makes. So a create also gets the rendered page's structural digest (the heading outline and the
 * link, image and word counts SH1013 reads from `/agent/render`) and a preview link. A page that
 * cannot render yet, with no layout, simply has no digest; that is a fact about the page, and the
 * panel says so rather than raising an error.</p>
 */
export function CreatedPagePanel({ postId }: { postId: string }) {
  const { t } = useTranslation()
  const [digest, setDigest] = useState<ShPageDigest | null | undefined>(undefined)

  useEffect(() => {
    const abort = new AbortController()
    insightsService.digest(`id:${postId}`, { signal: abort.signal }).then((answer) => {
      if (!abort.signal.aborted) setDigest(answer)
    })
    return () => abort.abort()
  }, [postId])

  const preview = () => {
    previewService.mint(postId)
      .then(({ previewUrl }) => { window.open(previewUrl, "_blank", "noopener") })
      .catch((error: unknown) => reportProblem(error, t("content.previewError")))
  }

  return (
    <div className="flex flex-wrap items-start justify-between gap-3 rounded-lg border p-3 text-sm">
      <div className="space-y-1">
        <p className="font-medium">{t("content.createdPage")}</p>
        {digest === undefined ? null : digest ? (
          <>
            <p className="text-muted-foreground">
              {t("content.createdDigest", {
                words: digest.words, headings: digest.headings.length,
                links: digest.links.length, images: digest.images.length,
              })}
            </p>
            {digest.headings.length > 0 && (
              <ul className="text-xs text-muted-foreground">
                {digest.headings.slice(0, 6).map((heading, index) => (
                  <li key={`${heading.level}-${index}`} style={{ paddingLeft: `${(heading.level - 1) * 0.75}rem` }}>
                    {heading.text}
                  </li>
                ))}
              </ul>
            )}
          </>
        ) : (
          <p className="text-muted-foreground">{t("content.createdNoDigest")}</p>
        )}
      </div>
      <Button size="sm" variant="outline" onClick={preview}>
        <Eye className="mr-1 size-4" /> {t("content.preview")}
      </Button>
    </div>
  )
}
