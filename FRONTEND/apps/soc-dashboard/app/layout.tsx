import type { ReactNode } from "react";
import "./globals.css";
import { ErrorBoundary } from "../components/ErrorBoundary";

export const metadata = {
  title: "GreenOps Console · FinOps and Zero Trust",
  description: "Cloud cost optimization and continuous identity operations console",
};

export default function RootLayout({ children }: { children: ReactNode }): ReactNode {
  return <html lang="en"><body><ErrorBoundary>{children}</ErrorBoundary></body></html>;
}
