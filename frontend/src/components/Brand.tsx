import { Boxes } from "lucide-react";
export default function Brand({ light = false }: { light?: boolean }) {
    return (
        <div className={`brand ${light ? "brand-light" : ""}`}>
            <span className="brand-icon">
                <Boxes size={24} strokeWidth={1.8} />
            </span>
            <span>
                Supply<span className="brand-go">Go</span>
                <small>CONNECTED COMMERCE</small>
            </span>
        </div>
    );
}
