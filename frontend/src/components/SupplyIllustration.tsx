/** Decorative vector art, independent of live route/location data. */
export default function SupplyIllustration() {
    return (
        <svg
            className="supply-art"
            viewBox="0 0 600 400"
            fill="none"
            aria-hidden="true"
        >
            <ellipse
                cx="302"
                cy="330"
                rx="240"
                ry="40"
                fill="#087f80"
                opacity=".13"
            />
            <path
                d="M80 275C100 160 205 320 290 220S460 135 525 235"
                stroke="#94e4ce"
                strokeWidth="28"
                strokeLinecap="round"
            />
            <path
                d="M80 275C100 160 205 320 290 220S460 135 525 235"
                stroke="white"
                strokeWidth="2"
                strokeDasharray="9 12"
            />
            <g transform="translate(350 135)">
                <rect width="149" height="117" rx="10" fill="#e9fff6" />
                <path d="M-13 12L74-45L160 12" fill="#ffa85c" />
                <rect
                    x="38"
                    y="46"
                    width="73"
                    height="71"
                    rx="4"
                    fill="#83ceba"
                />
                <path
                    d="M45 60H104M45 74H104M45 88H104"
                    stroke="#ddfff1"
                    strokeWidth="4"
                />
                <rect
                    x="12"
                    y="34"
                    width="16"
                    height="25"
                    rx="3"
                    fill="#48a5a1"
                />
            </g>
            <g transform="translate(112 213)">
                <rect width="146" height="77" rx="9" fill="#faf5dd" />
                <path d="M146 21H186L217 56V94H146V21Z" fill="#ffbb62" />
                <path d="M157 31H181L202 56H157V31Z" fill="#147d87" />
                <rect
                    x="-7"
                    y="77"
                    width="230"
                    height="18"
                    rx="5"
                    fill="#164a5c"
                />
                <circle cx="32" cy="96" r="20" fill="#163a50" />
                <circle cx="32" cy="96" r="9" fill="#d3eee2" />
                <circle cx="177" cy="96" r="20" fill="#163a50" />
                <circle cx="177" cy="96" r="9" fill="#d3eee2" />
                <path
                    d="M55 32L72 22L89 32V52L72 63L55 52V32Z"
                    fill="#11a58f"
                />
                <path
                    d="M55 32L72 42L89 32M72 42V63"
                    stroke="white"
                    strokeWidth="2"
                />
                <path
                    d="M100 40H130M100 49H122"
                    stroke="#164a5c"
                    strokeWidth="4"
                    strokeLinecap="round"
                />
            </g>
            <g transform="translate(66 111)">
                <rect width="95" height="71" rx="13" fill="white" />
                <circle cx="26" cy="28" r="12" fill="#def7ed" />
                <path
                    d="M20 28L24 32L33 23"
                    stroke="#12aa87"
                    strokeWidth="3"
                    strokeLinecap="round"
                />
                <path
                    d="M47 24H77M47 33H66M18 51H76"
                    stroke="#c7dbdc"
                    strokeWidth="5"
                    strokeLinecap="round"
                />
            </g>
            <g transform="translate(296 65)">
                <circle r="28" fill="#ffd98e" />
                <path
                    d="M-9 1L-2 8L12-8"
                    stroke="#94651f"
                    strokeWidth="4"
                    strokeLinecap="round"
                />
            </g>
            <circle cx="490" cy="77" r="8" fill="#ffc477" />
            <circle cx="208" cy="80" r="4" fill="#bbf4df" />
            <path
                d="M520 297V263M503 280H537"
                stroke="#b3edd8"
                strokeWidth="3"
            />
        </svg>
    );
}
