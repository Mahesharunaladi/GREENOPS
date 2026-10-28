import { NextRequest, NextResponse } from "next/server";

const backendUrl = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

export async function middleware(request: NextRequest) {
  const sessionToken = request.cookies.get("greenops_session")?.value;
  if (!sessionToken) return NextResponse.redirect(new URL("/login", request.url));

  try {
    const response = await fetch(`${backendUrl}/api/v1/auth/session`, {
      headers: { cookie: `greenops_session=${sessionToken}` },
      cache: "no-store",
    });
    if (response.ok) return NextResponse.next();
  } catch {
    // If the auth service is unreachable, fail closed and ask the user to sign in again.
  }

  const login = new URL("/login", request.url);
  login.searchParams.set("next", request.nextUrl.pathname);
  return NextResponse.redirect(login);
}

export const config = {
  matcher: ["/console/:path*"],
};
