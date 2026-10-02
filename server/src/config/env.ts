import { config as loadDotenv } from "dotenv";

loadDotenv();

function required(name: string): string {
  const value = process.env[name];
  if (!value || value.trim() === "") {
    throw new Error(
      `Missing required environment variable ${name}. Copy server/.env.example to server/.env and fill it in.`
    );
  }
  return value;
}

function parsePort(raw: string | undefined): number {
  const port = Number(raw ?? 3000);
  if (!Number.isInteger(port) || port <= 0 || port > 65535) {
    throw new Error(`Invalid PORT: ${raw}`);
  }
  return port;
}

export const env = {
  databaseUrl: required("DATABASE_URL"),
  nodeEnv: process.env.NODE_ENV ?? "development",
  port: parsePort(process.env.PORT),
};

// A function, not a plain property on `env`: `env` is imported by
// db/pool.ts and therefore by scripts (migrate.ts, verify-schema.ts)
// that have nothing to do with QR auth and shouldn't need API_BASE_URL
// set just to run a migration. Evaluating this lazily, only when
// something actually builds an activation URL, keeps that requirement
// scoped to the code that needs it.
export function getApiBaseUrl(): string {
  return required("API_BASE_URL");
}

// Deliberately optional, unlike every required() value above: trailers
// are a nice-to-have on top of the core app, not something that should
// stop the whole server from booting in an environment where nobody's
// gotten around to configuring a TMDB key yet. trailerService.ts reads
// this per-lookup (not once at startup) and treats null the same as "no
// trailer found" -- the Detail screen just doesn't show a Trailer button.
export function getTmdbReadAccessToken(): string | null {
  const value = process.env.TMDB_READ_ACCESS_TOKEN;
  return value && value.trim() !== "" ? value : null;
}

export type PlusPlanId = "monthly" | "yearly" | "lifetime";

export interface PlusConfig {
  /** False (the default): Plus is in early access and every account has it. True: only paying accounts do. */
  paywallOn: boolean;
  stripeSecretKey: string | null;
  stripeWebhookSecret: string | null;
  /** Stripe Price ids, one per plan. A plan with no price id can't be bought. */
  prices: Record<PlusPlanId, string | null>;
  /** Where Stripe sends the person after paying / backing out (a small page on this backend). */
  successUrl: string;
  cancelUrl: string;
}

const optional = (name: string): string | null => {
  const value = process.env[name];
  return value && value.trim() !== "" ? value.trim() : null;
};

/**
 * Read per request, not once at start-up, like the other optional integrations: the paywall is switched on by
 * setting PLUS_PAYWALL=on (and the Stripe values) in the host's environment, and nothing here may stop the server
 * booting when they are absent.
 */
export function getPlusConfig(): PlusConfig {
  const base = optional("API_BASE_URL") ?? "";
  return {
    paywallOn: (optional("PLUS_PAYWALL") ?? "off").toLowerCase() === "on",
    stripeSecretKey: optional("STRIPE_SECRET_KEY"),
    stripeWebhookSecret: optional("STRIPE_WEBHOOK_SECRET"),
    prices: {
      monthly: optional("STRIPE_PRICE_MONTHLY"),
      yearly: optional("STRIPE_PRICE_YEARLY"),
      lifetime: optional("STRIPE_PRICE_LIFETIME"),
    },
    successUrl: optional("PLUS_SUCCESS_URL") ?? `${base}/plus/thanks`,
    cancelUrl: optional("PLUS_CANCEL_URL") ?? `${base}/plus/thanks?cancelled=1`,
  };
}
