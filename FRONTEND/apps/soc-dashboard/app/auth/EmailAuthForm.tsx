"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import type { FormEvent } from "react";
import { ArrowLeft, CheckCircle2, Leaf, LoaderCircle, MailCheck } from "lucide-react";

type AuthMode = "login" | "register";
type AuthResponse = { message?: string; name?: string };

const backendUrl = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

export function EmailAuthForm({ mode }: { mode: AuthMode }) {
  const router = useRouter();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [step, setStep] = useState<"credentials" | "code">("credentials");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function requestCode(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    setError("");
    setMessage("");
    setBusy(true);
    try {
      const endpoint = mode === "register" ? "/api/v1/auth/register" : "/api/v1/auth/login";
      const body = mode === "register" ? { name, email, password } : { email, password };
      const response = await fetch(`${backendUrl}${endpoint}`, {
        method: "POST",
        credentials: "include",
        headers: { "content-type": "application/json", accept: "application/json" },
        body: JSON.stringify(body),
      });
      const result = await response.json() as AuthResponse;
      if (!response.ok) throw new Error(result.message ?? "Could not send a verification email.");
      setStep("code");
      setMessage(result.message ?? "Check your email for a verification code.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not reach the GreenOps server.");
    } finally {
      setBusy(false);
    }
  }

  async function verifyCode(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setMessage("");
    setBusy(true);
    try {
      const response = await fetch(`${backendUrl}/api/v1/auth/verify`, {
        method: "POST",
        credentials: "include",
        headers: { "content-type": "application/json", accept: "application/json" },
        body: JSON.stringify({ email, code }),
      });
      const result = await response.json() as AuthResponse;
      if (!response.ok) throw new Error(result.message ?? "That verification code could not be accepted.");
      router.replace("/console");
      router.refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not verify this code.");
    } finally {
      setBusy(false);
    }
  }

  const isRegister = mode === "register";
  return <main className="grid min-h-screen place-items-center bg-slate-50 px-5 py-10">
    <section className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8">
      <Link href="/" className="flex items-center gap-2 text-sm font-extrabold tracking-[0.12em] text-slate-800"><span className="grid h-8 w-8 place-items-center rounded-lg bg-emerald-600 text-white"><Leaf size={17}/></span>GREENOPS</Link>
      <div className="mt-9 grid h-10 w-10 place-items-center rounded-xl bg-emerald-50 text-emerald-700">{step === "code" ? <MailCheck size={19}/> : <CheckCircle2 size={19}/>}</div>
      <h1 className="mt-4 text-2xl font-bold text-slate-950">{step === "code" ? "Check your email" : isRegister ? "Create your workspace" : "Welcome back"}</h1>
      <p className="mt-2 text-sm leading-6 text-slate-500">{step === "code" ? <>Enter the 6-digit code sent to <strong className="font-semibold text-slate-700">{email}</strong>. Codes expire after 10 minutes.</> : isRegister ? "Create an account and verify your email to continue." : "Sign in with your email and password. We’ll send a verification code before you continue."}</p>

      {step === "credentials" ? <form className="mt-7 space-y-4" onSubmit={(event) => void requestCode(event)}>
        {isRegister && <label className="block text-sm font-medium text-slate-700">Full name<input autoComplete="name" className="mt-2 block w-full rounded-lg border border-slate-200 px-3 py-3 outline-none focus:border-emerald-500" type="text" maxLength={120} value={name} onChange={(event) => setName(event.target.value)} placeholder="Alex Morgan" required /></label>}
        <label className="block text-sm font-medium text-slate-700">Work email<input autoComplete="email" className="mt-2 block w-full rounded-lg border border-slate-200 px-3 py-3 outline-none focus:border-emerald-500" type="email" maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@company.com" required /></label>
        <label className="block text-sm font-medium text-slate-700">Password<input autoComplete={isRegister ? "new-password" : "current-password"} className="mt-2 block w-full rounded-lg border border-slate-200 px-3 py-3 outline-none focus:border-emerald-500" type="password" minLength={10} maxLength={128} value={password} onChange={(event) => setPassword(event.target.value)} placeholder={isRegister ? "At least 10 characters" : "Your password"} required /></label>
        <button className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-slate-900 px-4 py-3 text-sm font-semibold text-white hover:bg-slate-800 disabled:cursor-wait disabled:opacity-60" disabled={busy} type="submit">{busy && <LoaderCircle size={16} className="animate-spin"/>}{busy ? "Sending code…" : isRegister ? "Create account and email code" : "Continue and email code"}</button>
      </form> : <form className="mt-7 space-y-4" onSubmit={(event) => void verifyCode(event)}>
        <label className="block text-sm font-medium text-slate-700">Email verification code<input autoComplete="one-time-code" inputMode="numeric" pattern="[0-9]{6}" maxLength={6} className="mt-2 block w-full rounded-lg border border-slate-200 px-3 py-3 text-center font-mono text-xl tracking-[0.4em] outline-none focus:border-emerald-500" value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))} placeholder="000000" required /></label>
        <button className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-slate-900 px-4 py-3 text-sm font-semibold text-white hover:bg-slate-800 disabled:cursor-wait disabled:opacity-60" disabled={busy || code.length !== 6} type="submit">{busy && <LoaderCircle size={16} className="animate-spin"/>}{busy ? "Verifying…" : "Verify and continue"}</button>
        <button className="w-full rounded-lg border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-60" disabled={busy} onClick={() => void requestCode()} type="button">Send a new code</button>
        <button className="inline-flex w-full items-center justify-center gap-2 py-1 text-xs font-medium text-slate-500 hover:text-slate-800" onClick={() => { setStep("credentials"); setCode(""); setMessage(""); setError(""); }} type="button"><ArrowLeft size={14}/>Use a different email or password</button>
      </form>}

      {message && <p className="mt-4 rounded-lg border border-emerald-100 bg-emerald-50 p-3 text-sm text-emerald-800" role="status">{message}</p>}
      {error && <p className="mt-4 rounded-lg border border-rose-100 bg-rose-50 p-3 text-sm text-rose-700" role="alert">{error}</p>}
      <p className="mt-6 text-center text-sm text-slate-500">{isRegister ? "Already registered? " : "New to GREENOPS? "}<Link href={isRegister ? "/login" : "/register"} className="font-semibold text-emerald-700">{isRegister ? "Log in" : "Create an account"}</Link></p>
    </section>
  </main>;
}
