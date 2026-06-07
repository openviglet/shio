// GraphiQL v5 renders via Monaco web workers; this side-effect import wires them
// up (in app source, not graphiql's dep entry — see monaco-workers.ts) and must
// run before <GraphiQL/> mounts.
import "@/lib/monaco-workers"
import "graphiql/style.css"

import { GraphiQL } from "graphiql"
import { createGraphiQLFetcher } from "@graphiql/toolkit"
import { IconBrandGraphql } from "@tabler/icons-react"
import { KeyRound, Info } from "lucide-react"
import { useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"

import { PageHeader } from "@/components/page-header"
import { useTheme } from "@/components/theme-provider"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
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
    const { resetBreadcrumb } = useBreadcrumb()

    // null = still loading; [] = loaded but none usable.
    const [tokens, setTokens] = useState<ShApiToken[] | null>(null)
    const [selectedId, setSelectedId] = useState<string>("")

    useEffect(() => {
        resetBreadcrumb([{ label: t("graphql.title") }])
    }, [t, resetBreadcrumb])

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

    if (tokens.length === 0) {
        return (
            <>
                <PageHeader title={t("graphql.title")} turIcon={IconBrandGraphql} />
                <div className="flex flex-col items-center justify-center py-16 text-center">
                    <div className="rounded-full bg-muted p-4 mb-4">
                        <KeyRound className="h-8 w-8 text-muted-foreground" />
                    </div>
                    <h3 className="font-semibold text-lg mb-1">{t("graphql.noTokensTitle")}</h3>
                    <p className="text-sm text-muted-foreground max-w-sm mb-6">
                        {t("graphql.noTokensDescription")}
                    </p>
                    <Button onClick={() => navigate(`${ROUTES.ADMIN_TOKENS}/new`)}>
                        <KeyRound className="mr-2 h-4 w-4" />
                        {t("graphql.createToken")}
                    </Button>
                </div>
            </>
        )
    }

    return (
        <div className="flex flex-1 flex-col h-[calc(100vh-theme(spacing.16))] w-full">
            <PageHeader title={t("graphql.title")} turIcon={IconBrandGraphql} />

            <div className="flex items-center gap-3 border-b bg-muted/30 px-4 py-2">
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
            </div>

            <div className="min-h-0 flex-1">
                <GraphiQL fetcher={fetcher} forcedTheme={resolvedTheme} />
            </div>
        </div>
    )
}
