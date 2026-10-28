import type { ReactNode } from "react";
import { EmailAuthForm } from "../auth/EmailAuthForm";

export default function RegisterPage(): ReactNode {
  return <EmailAuthForm mode="register" />;
}
