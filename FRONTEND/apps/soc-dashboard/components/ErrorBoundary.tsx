"use client";

import { Component, type ErrorInfo, type ReactNode } from "react";

interface Props { children: ReactNode }
interface State { hasError: boolean }

export class ErrorBoundary extends Component<Props, State> {
  public state: State = { hasError: false };

  public static getDerivedStateFromError(): State { return { hasError: true }; }

  public componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error("GREENOPS dashboard rendering error", error, info.componentStack);
  }

  public render(): ReactNode {
    if (!this.state.hasError) return this.props.children;
    return <main className="grid min-h-screen place-items-center bg-slate-50 p-6"><section className="max-w-md rounded-2xl border border-slate-200 bg-white p-6 text-center shadow-sm"><h1 className="text-xl font-bold text-slate-950">Dashboard unavailable</h1><p className="mt-2 text-sm text-slate-500">The analyst view encountered an unexpected error. Refresh to restart the protected session.</p><button className="mt-5 rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white" onClick={() => window.location.reload()} type="button">Refresh dashboard</button></section></main>;
  }
}
