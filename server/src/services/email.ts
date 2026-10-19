/**
 * Sends email through Resend (https://resend.com). Off until RESEND_API_KEY is set on the host: `emailConfigured()` is false and nothing
 * that needs email is offered. Read per call, like the other optional integrations, so setting the key needs no code change.
 */
export interface EmailMessage {
  to: string;
  subject: string;
  text: string;
  html: string;
}

const optional = (name: string): string | null => {
  const value = process.env[name];
  return value && value.trim() !== "" ? value.trim() : null;
};

export const emailConfigured = (): boolean => optional("RESEND_API_KEY") !== null;

/** Who the email is from; the domain must be verified in Resend. */
export const mailFrom = (): string => optional("MAIL_FROM") ?? "Arc TV <no-reply@arctv.org>";

/** Swappable for tests, which must never reach the real Resend API. */
export type MailFetch = (url: string, init: { method: string; headers: Record<string, string>; body: string; signal: AbortSignal }) => Promise<{ ok: boolean; status: number; text(): Promise<string> }>;
let mailFetch: MailFetch = (url, init) => fetch(url, init);
export const setMailFetch = (impl: MailFetch | null): void => {
  mailFetch = impl ?? ((url, init) => fetch(url, init));
};

/** Sends one email. Throws when it can't (the caller decides whether that matters); the key is never in the error. */
export async function sendEmail(message: EmailMessage): Promise<void> {
  const key = optional("RESEND_API_KEY");
  if (!key) throw new Error("Email is not configured (RESEND_API_KEY)");
  const response = await mailFetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
    body: JSON.stringify({ from: mailFrom(), to: [message.to], subject: message.subject, text: message.text, html: message.html }),
    signal: AbortSignal.timeout(10_000),
  });
  if (!response.ok) {
    const detail = (await response.text().catch(() => "")).slice(0, 300);
    throw new Error(`Resend refused the email (HTTP ${response.status})${detail ? `: ${detail}` : ""}`);
  }
}
