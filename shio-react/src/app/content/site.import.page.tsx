/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconUpload } from "@tabler/icons-react"
import { GradientButton, toast } from "@viglet/viglet-design-system"
import { BentoFormHero, BentoFormSection } from "@viglet/viglet-design-system/bento"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { ShSiteService } from "@/services/site/site.service"
import { reportProblem } from "@/lib/problem"

const service = new ShSiteService()

/**
 * SH899 — importing a site, on its own hero.
 *
 * ## A page that saves imperatively passes its own buttons
 *
 * There is no `<form>` here and no react-hook-form: the action is "upload this zip", driven by a
 * button. The authoring contract has a rule for exactly that — pass the buttons through
 * `BentoFormHero`'s `actions`, which **replaces the default pair in both copies of the morph**
 * and keeps the two halves in step. Rendering a button below the fields instead would have left
 * the hero's own Save doing nothing, which is the shape a reader trusts least.
 */
export default function BentoSiteImportPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [file, setFile] = useState<File | null>(null)
  const [importing, setImporting] = useState(false)

  async function onImport() {
    if (!file) return
    setImporting(true)
    try {
      const { site, problems } = await service.import(file)
      // SH1079 — a skipped post or a missing type is said here, not left for the curator to find.
      if (problems.length > 0) {
        toast.warning(t("content.importProblems", {
          count: problems.length,
          first: `${problems[0].subject ?? ""} — ${problems[0].message}`,
        }))
      } else {
        toast.success(t("content.importSite"))
      }
      navigate(`${ROUTES.CONSOLE}/content/object/${site.id}`)
    } catch (error) {
      // SH1139 — a create that toasts success and lets a refusal escape as an unhandled rejection
      // looks exactly like a button that did nothing. The dock reads toasts, so the curator, the
      // mascot and a screen reader all missed it.
      reportProblem(error, t("common.error"))
    } finally {
      setImporting(false)
    }
  }

  return (
    <div className="space-y-6">
      <BentoFormHero
        eyebrow={t("content.siteLabel")}
        backTo={ROUTES.BENTO_CONTENT}
        backLabel={t("content.sites")}
        title={t("content.importSite")}
        subtitle={t("content.import")}
        onCancel={() => navigate(ROUTES.BENTO_CONTENT)}
        loading={importing}
        dirty={Boolean(file)}
        stickyTitle={t("content.importSite")}
        actions={
          <GradientButton onClick={onImport} disabled={!file} loading={importing}>
            {!importing && <IconUpload size={16} aria-hidden="true" className="mr-2" />}
            {t("content.uploadFile")}
          </GradientButton>
        }
      />

      {/* Same rule as the other three: the hero says this, so the section does not. */}
      <BentoFormSection icon={IconUpload} title={t("content.import")}>
        <div className="space-y-2">
          <Label htmlFor="import-file">{t("content.selectFile")}</Label>
          <Input
            id="import-file"
            type="file"
            accept=".zip,application/zip"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
        </div>
      </BentoFormSection>
    </div>
  )
}
