// ============================================================
// KLIX POS - PayChangu Webhook Handler
// Receives payment confirmations from PayChangu
// Verifies HMAC-SHA256 signature before processing
// ============================================================

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { crypto } from "https://deno.land/std@0.168.0/crypto/mod.ts";
import { encodeHex } from "https://deno.land/std@0.168.0/encoding/hex.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const PAYCHANGU_WEBHOOK_SECRET = Deno.env.get("PAYCHANGU_WEBHOOK_SECRET")!;

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

// ============================================================
// HMAC-SHA256 VERIFICATION
// ============================================================

async function verifySignature(
  rawBody: string,
  signatureHeader: string | null
): Promise<boolean> {
  if (!signatureHeader) return false;

  // Expected format: "sha256=hexdigest"
  const expectedPrefix = "sha256=";
  if (!signatureHeader.startsWith(expectedPrefix)) return false;

  const receivedSig = signatureHeader.substring(expectedPrefix.length);

  // Compute HMAC-SHA256 of raw body
  const key = new TextEncoder().encode(PAYCHANGU_WEBHOOK_SECRET);
  const data = new TextEncoder().encode(rawBody);

  const keyBytes = await crypto.subtle.importKey(
    "raw",
    key,
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const signature = await crypto.subtle.sign("HMAC", keyBytes, data);
  const computedSig = encodeHex(new Uint8Array(signature));

  // Constant-time comparison to prevent timing attacks
  if (computedSig.length !== receivedSig.length) return false;

  let result = 0;
  for (let i = 0; i < computedSig.length; i++) {
    result |= computedSig.charCodeAt(i) ^ receivedSig.charCodeAt(i);
  }
  return result === 0;
}

// ============================================================
// SUBSCRIPTION ACTIVATION
// ============================================================

async function activateSubscription(txRef: string, webhookPayload: any): Promise<void> {
  // 1. Find the pending transaction
  const { data: tx, error: txError } = await supabase
    .from("payment_transactions")
    .select("id, subscription_id, amount, status, tenant_id")
    .eq("tx_ref", txRef)
    .single();

  if (txError || !tx) {
    throw new Error(`Transaction not found for tx_ref: ${txRef}`);
  }

  // 2. Replay protection — already processed
  if (tx.status === "success") {
    console.log(`Duplicate webhook — tx_ref ${txRef} already processed`);
    return;
  }

  // 3. Verify amount matches
  const expectedAmount = tx.amount;
  const receivedAmount = webhookPayload.amount;
  if (Math.abs(receivedAmount - expectedAmount) > 1) {
    throw new Error(
      `Amount mismatch: expected ${expectedAmount}, received ${receivedAmount}`
    );
  }

  // 4. Update transaction as success
  const { error: updateTxError } = await supabase
    .from("payment_transactions")
    .update({
      status: "success",
      transaction_id: webhookPayload.transaction_id || webhookPayload.id?.toString(),
      gateway_response: webhookPayload,
    })
    .eq("id", tx.id);

  if (updateTxError) {
    throw new Error(`Failed to update transaction: ${updateTxError.message}`);
  }

  // 5. If there's already a subscription, update it
  if (tx.subscription_id) {
    const { error: subError } = await supabase
      .from("subscriptions")
      .update({
        status: "active",
        current_period_end: new Date(
          Date.now() + 30 * 24 * 60 * 60 * 1000
        ).toISOString(), // +30 days
      })
      .eq("id", tx.subscription_id);

    if (subError) {
      throw new Error(`Failed to update subscription: ${subError.message}`);
    }
  } else {
    // 6. No subscription exists — create one
    const { error: createSubError } = await supabase
      .from("subscriptions")
      .insert({
        tenant_id: tx.tenant_id,
        plan: webhookPayload.meta?.plan || "pro",
        status: "active",
        current_period_start: new Date().toISOString(),
        current_period_end: new Date(
          Date.now() + 30 * 24 * 60 * 60 * 1000
        ).toISOString(),
      });

    if (createSubError) {
      throw new Error(`Failed to create subscription: ${createSubError.message}`);
    }
  }

  // 7. Log success
  await supabase.from("system_logs").insert({
    level: "INFO",
    tag: "PAYMENT",
    message: `PayChangu payment confirmed: tx_ref=${txRef}, amount=${receivedAmount}`,
    metadata: webhookPayload,
  });
}

// ============================================================
// MAIN HANDLER
// ============================================================

serve(async (req: Request) => {
  try {
    // 1. Read raw body FIRST (before any parsing)
    const rawBody = await req.text();

    // 2. Verify HMAC signature
    const signature = req.headers.get("X-Paychangu-Signature");
    const isValid = await verifySignature(rawBody, signature);

    if (!isValid) {
      // Log failed verification
      try {
        await supabase.from("system_logs").insert({
          level: "WARN",
          tag: "SECURITY",
          message: "PayChangu webhook signature verification failed",
          metadata: { signature, body_preview: rawBody.substring(0, 200) },
        });
      } catch (_) {}

      return new Response("Invalid signature", { status: 401 });
    }

    // 3. Parse the verified body
    const payload = JSON.parse(rawBody);

    // 4. Only process completed payments
    if (payload.event !== "payment.completed" && payload.status !== "success") {
      return new Response("Event ignored", { status: 200 });
    }

    // 5. Activate the subscription
    await activateSubscription(payload.tx_ref, payload);

    return new Response("Webhook processed successfully", { status: 200 });
  } catch (error: any) {
    console.error("Webhook error:", error.message);

    // Log error, but always return 200 to prevent PayChangu from retrying
    try {
      await supabase.from("system_logs").insert({
        level: "ERROR",
        tag: "PAYMENT",
        message: `PayChangu webhook error: ${error.message}`,
        stack_trace: error.stack,
      });
    } catch (_) {}

    return new Response(JSON.stringify({ error: error.message }), {
      status: 200, // Return 200 so gateway doesn't keep retrying
      headers: { "Content-Type": "application/json" },
    });
  }
});
