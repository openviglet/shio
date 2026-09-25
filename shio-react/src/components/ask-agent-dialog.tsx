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
import { toast } from "@viglet/viglet-design-system"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Textarea } from "@/components/ui/textarea"
import { sendRequest } from "@/app/system/dock-chat-send"

/**
 * SH1019 — ask the connected agent for a value for one field.
 *
 * <p>A short prompt, prefilled from the field's label and editable, sent as a `request` note
 * anchored to the post and that field. The agent reads the field, its constraints and the page, and
 * replies with the value; the dock then offers to apply it into the form.</p>
 */
export function AskAgentDialog({ postId, field, onClose }: {
  postId: string
  field: { name: string; label: string }
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [text, setText] = useState(() => String(t("content.askAgentPrompt", { field: field.label })))
  const [sending, setSending] = useState(false)
  const say = (key: string, options?: Record<string, unknown>) => String(t(key, options))

  const send = async () => {
    if (!text.trim()) return
    setSending(true)
    const sent = await sendRequest(postId, text.trim(), say, field.name)
    setSending(false)
    if (sent) {
      toast.success(t("content.askAgentSent"))
      onClose()
    }
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("content.askAgentTitle", { field: field.label })}</DialogTitle>
          <DialogDescription>{t("content.askAgentHint")}</DialogDescription>
        </DialogHeader>
        <Textarea value={text} onChange={(event) => setText(event.target.value)} rows={3} />
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>{t("common.cancel")}</Button>
          <Button onClick={send} disabled={sending || !text.trim()}>{t("content.askAgentSend")}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
