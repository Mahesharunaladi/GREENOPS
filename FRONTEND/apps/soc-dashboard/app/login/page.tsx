import type { ReactNode } from "react";
import { EmailAuthForm } from "../auth/EmailAuthForm";

export default function LoginPage(): ReactNode {
  return <EmailAuthForm mode="login" />;
}
