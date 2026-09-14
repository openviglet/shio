// GraphiQL v5 renders via Monaco web workers; this side-effect import wires them
// up (in app source, not graphiql's dep entry — see monaco-workers.ts) and must
// run before <GraphiQL/> mounts.
import "@/lib/monaco-workers"
import "graphiql/style.css"

import { GraphiQL } from "graphiql"
import { createGraphiQLFetcher } from "@graphiql/toolkit"
import { IconBrandGraphql, IconKey } from "@tabler/icons-react"
import { KeyRound, Info } from "lucide-react"
import { useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"

import { EmptyState } from "@/components/empty-state"
import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { useTheme } from "@/components/theme-provider"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { ROUTES } from "@/app/routes.const"
import { ShApiTokenService } from "@/services/token/api-token.service"
import type { ShApiToken } from "@/models/token/api-token.model"

const tokenService = new ShApiTokenService()

/** Remembers the last CDA token picked in the explorer across visits. */
const STORAGE_KEY = "shio-graphql-token"

function resolveTheme(theme: string): "light" | "dark" {
    if (theme === "system") {
        return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light"
    }
    return theme === "dark" ? "dark" : "light"
}

export default function GraphiQLPage() {
    const { t } = useTranslation()
    const { theme } = useTheme()
    const navigate = useNavigate()

    // null = still loading; [] = loaded but none usable.
    const [tokens, setTokens] = useState<ShApiToken[] | null>(null)
    const [selectedId, setSelectedId] = useState<string>("")

    useEffect(() => {
    }, [t])

    useEffect(() => {
        tokenService.list()
            .then((list) => {
                const usable = list.filter((tk) => tk.enabled && tk.token)
                setTokens(usable)
                const remembered = localStorage.getItem(STORAGE_KEY)
                const initial = usable.find((tk) => tk.id === remembered) ?? usable[0]
                if (initial) setSelectedId(initial.id)
            })
            .catch(() => setTokens([]))
    }, [])

    const selectedToken = useMemo(
        () => tokens?.find((tk) => tk.id === selectedId),
        [tokens, selectedId],
    )

    // GraphQL delivery (SH4) is token-only: the console session has no ROLE_CDA, so
    // we authorize via the CDA token's `Key` header instead. credentials:"omit" keeps
    // the SHJSESSIONID cookie off the request — otherwise the session security context
    // (no ROLE_CDA) would load first and the Key header would be ignored (→ 403).
    const fetcher = useMemo(() => {
        const headers: Record<string, string> = selectedToken?.token ? { Key: selectedToken.token } : {}
        return createGraphiQLFetcher({
            url: "/graphql",
            headers,
            enableIncrementalDelivery: false,
            fetch: (input, init) => fetch(input, { ...init, credentials: "omit" }),
        })
    }, [selectedToken])

    const resolvedTheme = resolveTheme(theme)

    const handleSelect = (id: string) => {
        setSelectedId(id)
        localStorage.setItem(STORAGE_KEY, id)
    }

    if (tokens === null) {
        return (
            <div className="flex flex-1 items-center justify-center py-24">
                <div className="flex flex-col items-center gap-3 text-muted-foreground">
                    <IconBrandGraphql className="h-8 w-8 animate-pulse" />
                    <span className="text-sm">{t("graphql.loading")}</span>
                </div>
            </div>
        )
    }

    const header = (
        <PageHero
            icon={IconBrandGraphql}
            title={t("graphql.title")}
            eyebrow={t("admin.title")}
            subtitle={t("graphql.description")}
        />
    )

    if (tokens.length === 0) {
        return (
            <>
                {header}
                <div className="p-6">
                    <EmptyState
                        icon={IconKey}
                        tone="violet"
                        title={t("graphql.noTokensTitle")}
                        description={t("graphql.noTokensDescription")}
                        action={
                            <Button onClick={() => navigate(`${ROUTES.ADMIN_TOKENS}/new`)}>
                                <KeyRound className="mr-2 h-4 w-4" />
                                {t("graphql.createToken")}
                            </Button>
                        }
                    />
                </div>
            </>
        )
    }

    /*
     * SH908 — the height is the one thing this page cannot get from either shell.
     *
     * GraphiQL sizes itself to its container, so the container has to name a height, and the only
     * height available is the viewport minus whatever chrome sits above it. That constant is 4rem
     * in both eras for two *different* reasons: under the bento shell it is the `sticky top-0`
     * header the page renders below (SH907 measured the same 64px from the other side), and under
     * the console shell it is the footer `AppFooter` puts underneath. Two coincidences that agree
     * today — which is exactly the kind of premise SH907 says not to leave in a CSS expression, so
     * `bento-bespoke-slice.test.mjs` asserts both heights rather than trusting this comment.
     */
    return (
        <div className="flex flex-1 flex-col h-[calc(100vh-theme(spacing.16))] w-full">
            {header}

            {/*
              The toolbar is a `Panel`, not a `border-b bg-muted/30` strip: a hand-rolled strip under
              the frosted shell is SH902's defect — a page that composes the right components and
              still renders a surface of its own.
            */}
            <div className="px-4 pt-4 md:px-6">
                <Panel contentClassName="flex flex-wrap items-center gap-3 p-3">
                    <Label htmlFor="gql-token" className="text-sm font-medium whitespace-nowrap">
                        {t("graphql.token")}
                    </Label>
                    <Select value={selectedId} onValueChange={handleSelect}>
                        <SelectTrigger id="gql-token" className="w-72">
                            <SelectValue placeholder={t("graphql.tokenPlaceholder")} />
                        </SelectTrigger>
                        <SelectContent>
                            {tokens.map((tk) => (
                                <SelectItem key={tk.id} value={tk.id}>
                                    {tk.title} · {tk.scope ?? "READ"}
                                </SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                    <Tooltip>
                        <TooltipTrigger asChild>
                            <Info className="h-4 w-4 text-muted-foreground" />
                        </TooltipTrigger>
                        <TooltipContent className="max-w-xs">{t("graphql.tokenHelp")}</TooltipContent>
                    </Tooltip>
                    {!selectedToken && (
                        <span className="text-sm text-amber-600 dark:text-amber-400">
                            {t("graphql.noToken")}
                        </span>
                    )}
                </Panel>
            </div>

            <div className="min-h-0 flex-1 p-4 md:px-6">
                <GraphiQL fetcher={fetcher} forcedTheme={resolvedTheme} />
            </div>
        </div>
    )
}
