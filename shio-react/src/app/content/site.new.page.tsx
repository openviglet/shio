/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconWorld } from "@tabler/icons-react"
import { BentoFormHero, BentoFormSection } from "@viglet/viglet-design-system/bento"
import { toast } from "@viglet/viglet-design-system"
import { useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import type { ShSite } from "@/models/site/site"
import { ShSiteService } from "@/services/site/site.service"
import { reportProblem } from "@/lib/problem"

const service = new ShSiteService()

/**
 * SH899 — creating a site, on its own hero.
 *
 * ## Why `BentoFormHero` and not the entity shell
 *
 * The shell edits an entity that exists: it inline-edits identity in the hero and autosaves. A
 * create form has nothing to autosave into and its identity fields are the form. `BentoFormHero`
 * is the shape for that — it is the **first child inside the `<form>` it submits**, and it renders
 * *both halves of the save-bar morph itself*.
 *
 * That last part is the rule worth carrying: never compose a fade-out control group and a
 * `BentoScrollSaveBar` by hand. They are two halves that drift, and the authoring contract names
 * this exact mistake. The hero is given the form's id and its state, and it does the rest.
 */
export default function BentoSiteNewPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const form = useForm<ShSite>()
  const [saving, setSaving] = useState(false)

  const title = form.watch("name")

  async function onSubmit(data: ShSite) {
    setSaving(true)
    try {
      await service.create(data)
      toast.success(t("common.save"))
      navigate(ROUTES.BENTO_CONTENT)
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
          eyebrow={t("content.siteLabel")}
          backTo={ROUTES.BENTO_CONTENT}
          backLabel={t("content.sites")}
          title={title || t("content.newSite")}
          subtitle={t("content.createFirstSite")}
          onCancel={() => navigate(ROUTES.BENTO_CONTENT)}
          loading={saving}
          dirty={form.formState.isDirty}
          // The hero draws the "needs a title" marker itself; it needs telling, not styling.
          titleMissing={!title}
          saveDisabled={!title}
          stickyTitle={title || t("content.newSite")}
        />

        {/*
          No description, and no repeat of the hero's title either: the hero directly above says
          "New Site" and carries `content.createFirstSite` as its subtitle. A section that echoes
          the two lines above it is two lines of noise — SH898 made the same mistake on the home
          page and it is the easiest one to make, because both props want a string.
        */}
        <BentoFormSection icon={IconWorld} tone="blue" title={t("content.siteLabel")}>
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
          <FormField
            control={form.control}
            name="url"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t("content.url")}</FormLabel>
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
