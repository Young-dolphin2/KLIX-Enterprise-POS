// ============================================================
// KLIX POS - Create Checkout Edge Function
// Creates payment sessions on PayChangu or Flutterwave
// ============================================================

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// ============================================================
// CONSTANTS
// ============================================================

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const PAYCHANGU_SECRET_KEY = Deno.env.get("PAYCHANGU_SECRET_KEY")!;
const FLUTTERWAVE_SECRET_KEY = Deno.env.get("FLUTTERWAVE_SECRET_KEY")!;

const PRICING: Record<string, Record<string, number>> = {
  pro: { monthly: 15000, yearly: 150000 },
  enterprise: { monthly: 50000, yearly: 500000 },
};

const CURRENCIES: Record<string, string> = {
  paychangu: "MWK",
  flutterwave: "USD",
};

// ============================================================
// SUPABASE CLIENT
// ============================================================

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

// ============================================================
// HELPERS
// ============================================================

function generateTxRef(tenantId: string): string {
  const timestamp = Date.now();
  const random = Math.random().toString(36).substring(2, 8);
  return `klix_${tenantId.substring(0, 8)}_${timestamp}_${random}`;
}

function corsHeaders(): Record<string, string> {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
  };
}

// ============================================================
// PAYCHANGU PROVIDER
// ============================================================

async function createPayChanguCheckout(
  txRef: string,
  amount: number,
  email: string,
  phone: string,
  businessName: string,
  callbackUrl: string
): Promise<{ checkoutUrl: string; gatewayTxId: string | null }> {
  const response = await fetch("https://api.paychangu.com/v1/mpesa/checkout", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${PAYCHANGU_SECRET_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      tx_ref: txRef,
      amount: amount,
      currency: "MWK",
      phone_number: phone,
      network: phone.startsWith("26599") || phone.startsWith("26598") ? "airtel" : "tnm",
      callback_url: callbackUrl,
      return_url: `${callbackUrl}?tx_ref=${txRef}`,
      description: `KLIX POS Subscription - ${businessName}`,
      meta: {
        tenant_id: "", // Will be filled by caller
        tx_ref: txRef,
      },
    }),
  });

  if (!response.ok) {
    const errorBody = await response.text();
    throw new Error(`PayChangu checkout failed: ${response.status} - ${errorBody}`);
  }

  const data = await response.json();
  return {
    checkoutUrl: data.data?.checkout_url || data.checkout_url,
    gatewayTxId: data.data?.transaction_id || null,
  };
}

// ============================================================
// FLUTTERWAVE PROVIDER
// ============================================================

async function createFlutterwaveCheckout(
  txRef: string,
  amount: number,
  email: string,
  businessName: string,
  callbackUrl: string
): Promise<{ checkoutUrl: string; gatewayTxId: string | null }> {
  // Convert MWK to USD for Flutterwave (approximate)
  const usdAmount = Math.ceil(amount / 1800); // ~1 USD = 1800 MWK

  const response = await fetch("https://api.flutterwave.com/v3/payments", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${FLUTTERWAVE_SECRET_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      tx_ref: txRef,
      amount: usdAmount,
      currency: "USD",
      redirect_url: `${callbackUrl}?tx_ref=${txRef}`,
      customer: {
        email: email,
        name: businessName,
      },
      customizations: {
        title: "KLIX POS Subscription",
        description: "Premium POS Features",
        logo: "https://lwmbdrlogcordhubxbgy.supabase.co/storage/v1/object/public/assets/klix_logo.png",
      },
      payment_options: "card,mobilemoney",
      meta: {
        tenant_id: "",
        tx_ref: txRef,
      },
    }),
  });

  if (!response.ok) {
    const errorBody = await response.text();
    throw new Error(`Flutterwave checkout failed: ${response.status} - ${errorBody}`);
  }

  const data = await response.json();
  if (data.status !== "success") {
    throw new Error(`Flutterwave checkout failed: ${data.message}`);
  }

  return {
    checkoutUrl: data.data.link,
    gatewayTxId: data.data.id?.toString() || null,
  };
}

// ============================================================
// MAIN HANDLER
// ============================================================

serve(async (req: Request) => {
  // Handle CORS preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders() });
  }

  // Only accept POST
  if (req.method !== "POST") {
    return new Response(
      JSON.stringify({ error: "Method not allowed" }),
      { status: 405, headers: { ...corsHeaders(), "Content-Type": "application/json" } }
    );
  }

  try {
    const body = await req.json();
    const { gateway, plan, interval, tenant_id, email, phone, business_name } = body;

    // ── Validate input ────────────────────────────────────────
    if (!gateway || !["paychangu", "flutterwave"].includes(gateway)) {
      throw new Error("Invalid gateway. Must be 'paychangu' or 'flutterwave'");
    }
    if (!plan || !["pro", "enterprise"].includes(plan)) {
      throw new Error("Invalid plan. Must be 'pro' or 'enterprise'");
    }
    if (!interval || !["monthly", "yearly"].includes(interval)) {
      throw new Error("Invalid interval. Must be 'monthly' or 'yearly'");
    }
    if (!tenant_id) throw new Error("tenant_id is required");
    if (!email) throw new Error("email is required");
    if (gateway === "paychangu" && !phone) {
      throw new Error("phone is required for PayChangu");
    }

    // ── Calculate pricing ─────────────────────────────────────
    const amount = PRICING[plan][interval];
    const currency = CURRENCIES[gateway];
    const txRef = generateTxRef(tenant_id);

    // ── Call gateway API ──────────────────────────────────────
    const webhookBaseUrl = `${SUPABASE_URL}/functions/v1`;
    let checkoutUrl: string;
    let gatewayTxId: string | null;

    if (gateway === "paychangu") {
      const result = await createPayChanguCheckout(
        txRef,
        amount,
        email,
        phone,
        business_name || "KLIX User",
        `${webhookBaseUrl}/webhook-paychangu`
      );
      checkoutUrl = result.checkoutUrl;
      gatewayTxId = result.gatewayTxId;
    } else {
      const result = await createFlutterwaveCheckout(
        txRef,
        amount,
        email,
        business_name || "KLIX User",
        `${webhookBaseUrl}/webhook-flutterwave`
      );
      checkoutUrl = result.checkoutUrl;
      gatewayTxId = result.gatewayTxId;
    }

    // ── Insert payment_transaction record ─────────────────────
    const { data: tx, error: txError } = await supabase
      .from("payment_transactions")
      .insert({
        tenant_id: tenant_id,
        gateway: gateway,
        tx_ref: txRef,
        transaction_id: gatewayTxId,
        amount: gateway === "flutterwave" ? Math.ceil(amount / 1800) : amount,
        currency: currency,
        status: "pending",
      })
      .select("id")
      .single();

    if (txError) {
      throw new Error(`Failed to save transaction: ${txError.message}`);
    }

    // ── Log to system_logs ────────────────────────────────────
    await supabase.from("system_logs").insert({
      level: "INFO",
      tag: "PAYMENT",
      message: `Checkout created: gateway=${gateway}, plan=${plan}, interval=${interval}, amount=${amount} ${currency}, tx_ref=${txRef}`,
      metadata: {
        tenant_id,
        gateway,
        plan,
        interval,
        amount,
        currency,
        tx_ref: txRef,
      },
    });

    // ── Return success ────────────────────────────────────────
    return new Response(
      JSON.stringify({
        checkout_url: checkoutUrl,
        tx_ref: txRef,
        amount: amount,
        currency: currency,
        transaction_id: tx?.id,
      }),
      {
        status: 200,
        headers: { ...corsHeaders(), "Content-Type": "application/json" },
      }
    );
  } catch (error: any) {
    // Log the error
    try {
      await supabase.from("system_logs").insert({
        level: "ERROR",
        tag: "PAYMENT",
        message: `Create checkout failed: ${error.message}`,
        stack_trace: error.stack,
      });
    } catch (_) {
      // Silently fail — logging should never break the response
    }

    return new Response(
      JSON.stringify({ error: error.message || "Internal server error" }),
      {
        status: 400,
        headers: { ...corsHeaders(), "Content-Type": "application/json" },
      }
    );
  }
});
