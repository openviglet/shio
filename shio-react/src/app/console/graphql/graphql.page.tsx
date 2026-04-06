import { useTheme } from "@/components/theme-provider"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { useEffect, useRef } from "react"
import { useTranslation } from "react-i18next"

export default function GraphiQLPage() {
    const { theme } = useTheme()
    const iframeRef = useRef<HTMLIFrameElement>(null)
    const { t } = useTranslation()
    const { resetBreadcrumb } = useBreadcrumb()

    useEffect(() => {
        resetBreadcrumb([{ label: t("graphql.title") }])
    }, [t, resetBreadcrumb])

    useEffect(() => {
        if (!iframeRef.current || !iframeRef.current.contentWindow) return;

        let resolvedTheme = theme;
        if (theme === "system") {
            resolvedTheme = window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
        }

        try {
            iframeRef.current.contentWindow.localStorage.setItem("graphiql:theme", resolvedTheme);
            iframeRef.current.contentWindow.location.reload();
        } catch (e) {
            console.error("Could not set GraphiQL theme on iframe localStorage", e);
        }
    }, [theme])

    return (
        <div className="flex flex-1 flex-col h-[calc(100vh-theme(spacing.16))] w-full">
            <iframe
                ref={iframeRef}
                src="/graphiql"
                className="h-full w-full border-0 flex-1"
                title={t("graphql.title")}
            />
        </div>
    )
}
