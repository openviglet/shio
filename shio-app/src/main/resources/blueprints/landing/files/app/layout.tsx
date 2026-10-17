import type { ReactNode } from "react";

export const metadata = {
  title: "Shio site",
  description: "Powered by Viglet Shio — headless, JavaScript-first.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body
        style={{
          fontFamily: "system-ui, sans-serif",
          margin: 0,
          lineHeight: 1.6,
          color: "#1a1a1a",
        }}
      >
        <main style={{ maxWidth: 720, margin: "0 auto", padding: "2rem 1rem" }}>
          {children}
        </main>
      </body>
    </html>
  );
}
