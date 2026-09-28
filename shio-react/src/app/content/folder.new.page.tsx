/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconFolder } from "@tabler/icons-react"
import { toast } from "@viglet/viglet-design-system"
import { BentoFormHero, BentoFormSection } from "@viglet/viglet-design-system/bento"
import { useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import type { ShFolder } from "@/models/folder/folder"
import { ShFolderService } from "@/services/folder/folder.service"
import { reportProblem } from "@/lib/problem"

const service = new ShFolderService()

/**
 * SH900 — creating a folder inside another one.
 *
 * The create counterpart of the editor next door, and the same reasoning as SH899's site form:
 * a create has nothing to autosave into, so it is `BentoFormHero` inside the `<form>` it submits
 * and the hero renders both halves of the morph. The parent folder comes from the route, and the
 * page returns to it rather than to the content root — a curator who has just made a folder is
 * looking at where they made it.
 */
export default function BentoFolderNewPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { folderId = "" } = useParams()
  const form = useForm<Partial<ShFolder>>()
  const [saving, setSaving] = useState(false)

  const title = form.watch("name")
  const parent = `${ROUTES.BENTO_CONTENT}/object/${folderId}`

  async function onSubmit(data: Partial<ShFolder>) {
    setSaving(true)
    try {
      await service.create(data, folderId)
      toast.success(t("common.saved"))
      navigate(parent)
    } catch (error) {
      // SH1139 — a create that toasts success and lets a refusal escape as an unhandled rejection
      // looks exactly like a button that did nothing. The dock reads toasts, so the curator, the
      // mascot and a screen reader all missed it.
      reportProblem(error, t("common.error"))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
        <BentoFormHero
          eyebrow={t("content.folder")}
          backTo={parent}
          backLabel={t("content.browsing")}
          title={title || t("content.newFolder")}
          onCancel={() => navigate(parent)}
          loading={saving}
          dirty={form.formState.isDirty}
          titleMissing={!title}
          saveDisabled={!title}
          stickyTitle={title || t("content.newFolder")}
        />

        <BentoFormSection icon={IconFolder} tone="amber" title={t("content.folder")}>
          <FormField
            control={form.control}
            name="name"
            rules={{ required: t("common.required") }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t("common.name")}</FormLabel>
                <FormControl>
                  <Input {...field} value={field.value ?? ""} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="description"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t("common.description")}</FormLabel>
                <FormControl>
                  <Input {...field} value={field.value ?? ""} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </BentoFormSection>
      </form>
    </Form>
  )
}
