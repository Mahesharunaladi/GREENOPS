import type { ReactNode } from "react";
import "./globals.css";
import { ErrorBoundary } from "../components/ErrorBoundary";

export const metadata = { title: "GREENOPS SOC", description: "Zero Trust cloud security operations" };

export default function RootLayout({ children }: { children: ReactNode }): ReactNode {
  return <html lang="en"><body><ErrorBoundary>{children}</ErrorBoundary></body></html>;
}
