/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { useQuery } from "@tanstack/react-query"
import axios from "axios"
import { toast } from "@viglet/viglet-design-system"
import { IconCheck, IconCopy, IconKey, IconLoader2 } from "@tabler/icons-react"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { ShApiTokenService } from "@/services/token/api-token.service"
import type { ShApiToken } from "@/models/token/api-token.model"
import { claudeCommand, mcpEndpoint } from "@/lib/agent-connect"
import { reportProblem } from "@/lib/problem"

const tokenService = new ShApiTokenService()

/** How often the last step asks whether the agent has arrived. */
const POLL_MS = 3000

/** The rows `GET /v2/agent/runs?since=` answers with: a run that introduced itself. */
interface ShAgentRunRow {
  session: string
  clientName?: string | null
  startedAt?: string | null
}

/**
 * SH1421 — the fourth way a first site starts, and the one the product is designed around.
 *
 * <p>Shio is built for a coding agent first, yet a newcomer used to have to find the docs, learn
 * that the endpoint is `POST /mcp`, issue an AGENT key in the token list and assemble the command by
 * hand. This panel does those three in place: an explicit press issues an AGENT-scope key that may
 * not publish (the agent writes drafts and the curator publishes, P7), the command is shown with
 * this instance's URL and that key filled in, and the plugin install is named as the alternative.
 * The last step asks the server every few seconds whether a run has introduced itself since the key
 * was issued, so "it worked" is something the page says rather than something the newcomer checks.</p>
 */
export function ConnectAgentDialog({
  open,
  onOpenChange,
}: Readonly<{ open: boolean; onOpenChange: (open: boolean) => void }>) {
  const { t } = useTranslation()
  const [token, setToken] = useState<ShApiToken | null>(null)
  const [issuedAt, setIssuedAt] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [copied, setCopied] = useState(false)

  const endpoint = mcpEndpoint(axios.defaults.baseURL, window.location.origin)
  const command = token ? claudeCommand(endpoint, token.token) : ""

  const { data: arrived } = useQuery({
    queryKey: ["agent-runs-since", issuedAt],
    queryFn: ({ signal }) => axios.get<ShAgentRunRow[]>("/v2/agent/runs", { signal, params: { since: issuedAt } })
      .then((response) => response.data[0] ?? null),
    enabled: open && issuedAt !== null,
    // Stops asking once the answer is in: the panel has said what it set out to say.
    refetchInterval: (query) => (query.state.data ? false : POLL_MS),
  })

  const issue = async () => {
    setBusy(true)
    try {
      // The instant before the key exists, so a run that arrives in the same second still counts.
      const before = new Date().toISOString()
      const created = await tokenService.create({
        title: t("content.connectTokenTitle"),
        description: t("content.connectTokenDescription"),
        scope: "AGENT",
        env: "PROD",
        siteIds: [],
        mayPublish: false,
      } as unknown as ShApiToken)
      setToken(created)
      setIssuedAt(before)
      toast.success(t("content.connectKeyIssued", { title: created.title }))
    } catch (error) {
      reportProblem(error, t("content.connectTokenFailed"))
    } finally {
      setBusy(false)
    }
  }

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(command)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      toast.error(t("apiToken.copyFailed"))
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl" data-testid="connect-agent">
        <DialogHeader>
          <DialogTitle>{t("content.connectTitle")}</DialogTitle>
          <DialogDescription>{t("content.connectLead")}</DialogDescription>
        </DialogHeader>

        <ol className="space-y-5 text-sm">
          <li className="space-y-2">
            <p className="font-medium">{t("content.connectStepKey")}</p>
            <p className="text-muted-foreground">{t("content.connectStepKeyDesc")}</p>
            {token ? (
              <p className="flex items-center gap-1.5 text-sm">
                <IconCheck size={16} aria-hidden="true" className="text-primary" />
                {t("content.connectKeyIssued", { title: token.title })}
              </p>
            ) : (
              <Button size="sm" onClick={issue} disabled={busy}>
                <IconKey size={16} aria-hidden="true" className="mr-1.5" />
                {t("content.connectIssueKey")}
              </Button>
            )}
          </li>

          <li className="space-y-2">
            <p className="font-medium">{t("content.connectStepCommand")}</p>
            {token ? (
              <div className="flex items-start gap-2">
                <code className="bg-muted block min-w-0 flex-1 rounded-md px-3 py-2 font-mono text-xs break-all" data-testid="connect-command">
                  {command}
                </code>
                <Button size="icon" variant="outline" onClick={copy} aria-label={t("content.connectCopy")}>
                  {copied ? <IconCheck size={16} aria-hidden="true" /> : <IconCopy size={16} aria-hidden="true" />}
                </Button>
              </div>
            ) : (
              <p className="text-muted-foreground">{t("content.connectCommandWaits")}</p>
            )}
            <p className="text-muted-foreground">
              {t("content.connectPlugin")}{" "}
              <code className="font-mono text-xs">claude plugin marketplace add openviglet/shio</code>{" "}
              <code className="font-mono text-xs">claude plugin install shio</code>
            </p>
          </li>

          <li className="space-y-2">
            <p className="font-medium">{t("content.connectStepWait")}</p>
            {!token && <p className="text-muted-foreground">{t("content.connectWaitIdle")}</p>}
            {token && !arrived && (
              <p className="text-muted-foreground flex items-center gap-1.5" role="status">
                <IconLoader2 size={16} aria-hidden="true" className="animate-spin" />
                {t("content.connectWaiting")}
              </p>
            )}
            {arrived && (
              <p className="text-foreground flex items-center gap-1.5 font-medium" role="status" data-testid="connect-arrived">
                <IconCheck size={16} aria-hidden="true" className="text-primary" />
                {t("content.connectArrived", { client: arrived.clientName || t("content.connectAnAgent") })}
              </p>
            )}
          </li>
        </ol>
      </DialogContent>
    </Dialog>
  )
}
