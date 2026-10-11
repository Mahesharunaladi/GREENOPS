import type { ReactNode } from "react";
import "./globals.css";
import { GreenopsProvider } from "../lib/GreenopsProvider";

export const metadata = { title: "GREENOPS SOC", description: "Zero Trust cloud security operations" };

export default function RootLayout({ children }: { children: ReactNode }): ReactNode {
  return <html lang="en"><body><GreenopsProvider>{children}</GreenopsProvider></body></html>;
}
