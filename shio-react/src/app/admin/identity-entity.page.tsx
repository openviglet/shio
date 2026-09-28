/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import type { Icon as TablerIcon } from "@tabler/icons-react"
import { BentoEntityShell, type BentoTone } from "@viglet/viglet-design-system/bento"
import { toast } from "@viglet/viglet-design-system"
import { useQuery } from "@tanstack/react-query"
import { useEffect, useState, type ReactNode } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { reportProblem } from "@/lib/problem"
import { addressKey, type ShScheme } from "@/lib/query-keys"
import { isCancelled, type ShRead } from "@/lib/read-options"
import type { ShUser } from "@/models/auth/user"

/** The entity a create page starts from — module-level, so it is one stable object. */
const BLANK: IdentityEntity = { id: "", name: "", description: "" }

/** What a group and a role both are: an id, a name and a description. Nothing else. */
export interface IdentityEntity {
  id: string
  name: string
  description: string
  /** SH1200 — a group's members, which `GET /v2/group/{id}` carries and `PUT` replaces. */
  shUsers?: ShUser[] | null
}

/** The four calls this page needs. Both `ShGroupService` and `ShRoleService` already have them. */
export interface IdentityService {
  /** SH1116 — the read takes a cancellation signal, so `useQuery` can hang up when the id changes. */
  get(id: string, read?: ShRead): Promise<IdentityEntity>
  /**
   * Create takes an entity **with no id**, and that is not a nicety.
   *
   * The first draft passed `{ id: "", ... }` because `IdentityEntity` requires the field, and
   * the backend stored it verbatim: a row whose primary key is the empty string, listed by
   * `/v2/group` and addressable by nothing — `/v2/group/` is not `/v2/group/{id}`, so it could
   * not be opened, updated or deleted through the console that made it. The console form never
   * had this problem because its `useForm` only ever held `name` and `description`, so the key
   * was simply absent from the payload and the server minted one.
   *
   * Found by creating a group and reading it back, which is the only way it could be found:
   * the page navigated to the list, the group appeared on it, and every visible thing about
   * the flow was correct.
   */
  create(entity: Omit<IdentityEntity, "id">): Promise<unknown>
  update(entity: IdentityEntity): Promise<unknown>
  delete(id: string): Promise<unknown>
}

interface IdentityEntityPageProps {
  service: IdentityService
  /** Route param carrying the id, and the sentinel `"new"`. */
  param: string
  /**
   * SH1116 — the address scheme this entity is cached under: `"group:"` or `"role:"`.
   *
   * <p>Passed in rather than derived from `param`, because a cache key is an address (SH1046) and
   * `addressKey` throws on a scheme it does not know — a key built from a route parameter would be
   * one typo away from caching under a name no invalidation ever names.</p>
   */
  scheme: ShScheme
  listRoute: string
  icon: TablerIcon
  tone: BentoTone
  /** Section label above the hero title. */
  eyebrow: string
  /** Placeholder in the title inline-edit, and the toast's subject. */
  feature: string
  headlineFallback: string
  formId: string
  /**
   * SH1200 — what an existing entity shows under its hero, given a way to save a change to it. The
   * shell's hero holds the identity; this is the rest of the job, such as a group's members.
   */
  detail?: (entity: IdentityEntity, save: (next: IdentityEntity) => Promise<void>) => ReactNode
}

/**
 * SH904 — the group and role editors, which are the same page twice.
 *
 * ## Why one component and not two copies
 *
 * `ShGroup` and `ShRole` are byte-for-byte the same shape — `{id, name, description}` — with the
 * same four service calls and the same two routes. Two files would be two places to fix the next
 * defect in, and SH900 already made that argument for the content browser at 965 lines; it is not
 * weaker at 90. The console had them as two near-identical files and they had already drifted in
 * their breadcrumbs.
 *
 * ## The empty form body, and the button that would have been dead
 *
 * A group is `name` + `description`. Both are hero fields, so **the form body is empty** — the
 * shape that produced SH899's dead Save button, where the shell renders
 * `<GradientButton type="submit" form={formId}>` and no form of that id exists.
 *
 * Two different answers are needed, because the shell's two modes want different things:
 *
 * - **Existing entity**: `autosaveOnly`. The package's own line is
 *   `showSaveSubmit = !(autosaveOnly && !isNew)`, so no submit button renders at all and the
 *   hero's inline edits commit on blur through `onUpdate`. This is SH900's folder exactly.
 * - **New entity**: `autosaveOnly` deliberately does *not* suppress Save, because Save is what
 *   creates the entity — so here the button **is** rendered and it **does** need a real form to
 *   submit. Hence the empty `<form id={formId}>` below. It has no visible fields, which looks
 *   pointless until you remember that the alternative is a create page whose only button does
 *   nothing, silently, exactly as SH899 shipped.
 *
 * ## What §LV.7 predicted and what was actually there
 *
 * The section said "each of these entities has a title, a description and an enabled state". The
 * first two are `name`/`description` under other names; **the third does not exist** on either
 * entity, so `hasStatus` stays off. A status pill driven by a column that is not in the model
 * would have rendered "Idle" on every group in the instance.
 */
export function IdentityEntityPage({
  service,
  param,
  scheme,
  listRoute,
  icon,
  tone,
  eyebrow,
  feature,
  headlineFallback,
  formId,
  detail,
}: Readonly<IdentityEntityPageProps>) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const params = useParams()
  const id = params[param] ?? ""
  const isNew = id === "new"
  const [loaded, setLoaded] = useState<IdentityEntity | null>(null)

  /*
   * SH1116 — one read per entity, cancelled when the id changes or the screen unmounts.
   *
   * `service` was in the dependency list, which is an object identity rather than a subject: both
   * callers build theirs at module scope so it never changed, but it said nothing about *which*
   * entity was wanted. The key says exactly that.
   */
  const read = useQuery<IdentityEntity>({
    queryKey: addressKey(`${scheme}${id}`),
    queryFn: ({ signal }) => service.get(id, { signal }),
    enabled: !isNew && Boolean(id),
  })

  // Set during render rather than in an effect (`react-hooks/set-state-in-effect`), once per entity.
  const [seeded, setSeeded] = useState<string | null>(null)
  if (read.data && seeded !== id) {
    setSeeded(id)
    setLoaded(read.data)
  }

  useEffect(() => {
    // The read had no failure path: a group that would not load left the page on its blank form.
    if (read.error && !isCancelled(read.error)) reportProblem(read.error, t("common.error"))
  }, [read.error, t])

  /*
   * The blank entity is **derived**, not stored. Setting it from the effect is a synchronous
   * `setState` inside `useEffect`, which is a cascading render and an eslint error here (SH701) —
   * and it would have been a real extra render on every create page in the console.
   */
  const entity = isNew ? BLANK : loaded

  if (!entity) return null

  return (
    <BentoEntityShell
      entity={{ ...entity, title: entity.name, description: entity.description }}
      isNew={isNew}
      autosaveOnly
      headlineFallback={headlineFallback}
      eyebrow={eyebrow}
      listRoute={listRoute}
      icon={icon}
      tone={tone}
      formId={formId}
      feature={feature}
      hideIcon
      onUpdate={
        isNew
          ? undefined
          : async (next) => {
              const merged: IdentityEntity = {
                ...entity,
                name: next.title ?? entity.name,
                description: next.description ?? entity.description,
              }
              await service.update(merged)
              setLoaded(merged)
            }
      }
      onDelete={
        isNew
          ? undefined
          : async () => {
              await service.delete(entity.id)
              navigate(listRoute)
              return true
            }
      }
    >
      {({ staged }) =>
        isNew ? (
          /*
           * The form the hero's Save submits. Empty on purpose — every field a group has is a
           * hero field — but it must exist and carry `formId`, or the create button is inert.
           * `staged` is the hero's live identity, which is where the values come from.
           */
          <form
            id={formId}
            onSubmit={async (event) => {
              event.preventDefault()
              // SH1139 — a refused create used to escape as an unhandled rejection, leaving the
              // form sitting there with no toast: indistinguishable from a button that did nothing.
              try {
                await service.create({ name: staged.title, description: staged.description })
                toast.success(t("common.save"))
                navigate(listRoute)
              } catch (error) {
                reportProblem(error, t("common.error"))
              }
            }}
          />
        ) : detail ? detail(entity, async (next) => {
          await service.update(next)
          setLoaded(next)
        }) : null
      }
    </BentoEntityShell>
  )
}
