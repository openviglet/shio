import { useTheme } from "@/components/theme-provider"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { useCallback, useEffect, useRef } from "react"
import { useTranslation } from "react-i18next"

function resolveTheme(theme: string): string {
    if (theme === "system") {
        return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light"
    }
    return theme
}

export default function GraphiQLPage() {
    const { theme } = useTheme()
    const iframeRef = useRef<HTMLIFrameElement>(null)
    const { t } = useTranslation()
    const { resetBreadcrumb } = useBreadcrumb()
    const appliedThemeRef = useRef<string | null>(null)

    useEffect(() => {
        resetBreadcrumb([{ label: t("graphql.title") }])
    }, [t, resetBreadcrumb])

    const applyThemeToIframe = useCallback(() => {
        const iframe = iframeRef.current
        if (!iframe?.contentWindow || !iframe.contentDocument) return

        const resolved = resolveTheme(theme)

        // Skip if already applied
        if (appliedThemeRef.current === resolved) return
        appliedThemeRef.current = resolved

        try {
            // Method 1: Set localStorage key that GraphiQL reads
            iframe.contentWindow.localStorage.setItem("graphiql:theme", resolved)

            // Method 2: Inject a plugin attribute on the root element
            const root = iframe.contentDocument.querySelector(".graphiql-container")
                ?? iframe.contentDocument.querySelector("#graphiql")
                ?? iframe.contentDocument.documentElement
            if (root) {
                root.classList.toggle("graphiql-dark", resolved === "dark")
                root.classList.toggle("graphiql-light", resolved === "light")
            }

            // Method 3: Set data-theme and class on html/body
            const html = iframe.contentDocument.documentElement
            html.setAttribute("data-theme", resolved)
            html.classList.toggle("dark", resolved === "dark")
            html.classList.remove(resolved === "dark" ? "light" : "dark")

            const body = iframe.contentDocument.body
            if (body) {
                body.setAttribute("data-theme", resolved)
                body.classList.toggle("dark", resolved === "dark")
                body.classList.remove(resolved === "dark" ? "light" : "dark")
            }

            // Method 4: If nothing else worked, click the theme button
            const themeBtn = iframe.contentDocument.querySelector(
                'button[aria-label="Theme"], button[title="Theme"], .graphiql-toolbar button:last-child'
            )
            if (themeBtn) {
                // Check current state and click if needed
                const isDark = html.classList.contains("dark")
                    || html.getAttribute("data-theme") === "dark"
                    || iframe.contentWindow.localStorage.getItem("graphiql:theme") === "dark"
                if ((resolved === "dark" && !isDark) || (resolved === "light" && isDark)) {
                    (themeBtn as HTMLElement).click()
                }
            }
        } catch {
            // Cross-origin or timing issue — fall back to reload approach
            try {
                iframe.contentWindow.localStorage.setItem("graphiql:theme", resolved)
                iframe.contentWindow.location.reload()
            } catch {
                // Give up silently
            }
        }
    }, [theme])

    // Apply on iframe load
    const handleIframeLoad = useCallback(() => {
        appliedThemeRef.current = null // force re-apply
        // Small delay to let GraphiQL initialize
        setTimeout(applyThemeToIframe, 300)
    }, [applyThemeToIframe])

    // Apply when theme changes
    useEffect(() => {
        appliedThemeRef.current = null // force re-apply
        applyThemeToIframe()
    }, [theme, applyThemeToIframe])

    return (
        <div className="flex flex-1 flex-col h-[calc(100vh-theme(spacing.16))] w-full">
            <iframe
                ref={iframeRef}
                src="/graphiql"
                className="h-full w-full border-0 flex-1"
                title={t("graphql.title")}
                onLoad={handleIframeLoad}
            />
        </div>
    )
}
