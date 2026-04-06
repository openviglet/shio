interface ShLogoProps {
    readonly size?: number;
    readonly className?: string;
}

export function ShLogo({ size = 48, className }: ShLogoProps) {
    return (
        <svg
            className={className}
            width={size}
            height={size}
            viewBox="0 0 549 549"
        >
            <defs>
                <style>{`
                    .sh-logo-bg {
                        fill: tomato;
                        stroke: #fff4e6;
                        stroke-width: 20px;
                        opacity: 1.0;
                    }
                    .sh-logo-text {
                        font-size: 98.505px;
                        fill: #fff4e6;
                        font-family: "Proxima Nova", system-ui, sans-serif;
                        font-weight: 500;
                    }
                `}</style>
            </defs>
            <rect
                className="sh-logo-bg"
                x="0.063"
                width="548"
                height="548.188"
                rx="100"
                ry="100"
            />
            <text
                className="sh-logo-text"
                transform="translate(64.825 442.418) scale(2.74 2.741)"
            >
                Sh
            </text>
        </svg>
    );
}
