/**
 * Waqar Computer Institute ERP — Secure OpenAI/ChatGPT SSE Proxy (Firebase Cloud Functions v2)
 *
 * Security Architecture:
 * 1. The OpenAI API key (`OPENAI_API_KEY`) is stored ONLY in Google Cloud Secret Manager
 *    (`firebase functions:secrets:set OPENAI_API_KEY`) and NEVER embedded inside the Android APK.
 * 2. Every incoming request must present a valid `X-Firebase-AppCheck` header verified via
 *    `admin.appCheck().verifyToken(...)` before any upstream OpenAI call is made.
 * 3. Streams tokens in real time via Server-Sent Events (`text/event-stream`) with zero buffering.
 */

const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");

if (!admin.apps.length) {
  admin.initializeApp();
}

const OPENAI_API_KEY = defineSecret("OPENAI_API_KEY");

const RATE_LIMIT_MAX_REQUESTS = 5;
const RATE_LIMIT_WINDOW_MS = 60 * 1000;       // 60 seconds
const RATE_LIMIT_BLOCK_MS = 5 * 60 * 1000;    // 5 minutes (300 seconds)
const fallbackBuckets = new Map();

function applySecurityHeaders(res) {
  res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("X-Frame-Options", "DENY");
  res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
  res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
}

async function checkAndConsumeServerRateLimit(bucketKey, nowMs = Date.now()) {
  const safeKey = String(bucketKey || "anon").replace(/[^a-zA-Z0-9_.:@-]/g, "_").slice(0, 120);
  try {
    const db = admin.firestore();
    const docRef = db.collection("rateLimits").doc(safeKey);
    return await db.runTransaction(async (tx) => {
      const snap = await tx.get(docRef);
      const data = snap.exists ? snap.data() : null;
      if (data && typeof data.blockedUntilMs === "number" && nowMs < data.blockedUntilMs) {
        const retryAfterSec = Math.max(1, Math.ceil((data.blockedUntilMs - nowMs) / 1000));
        return { allowed: false, retryAfterSec, blockedUntilMs: data.blockedUntilMs };
      }
      const isNewWindow =
        !data ||
        (typeof data.blockedUntilMs === "number" && data.blockedUntilMs > 0 && nowMs >= data.blockedUntilMs) ||
        nowMs - (data.windowStartMs || 0) >= RATE_LIMIT_WINDOW_MS;
      const windowStartMs = isNewWindow ? nowMs : data.windowStartMs;
      const nextCount = isNewWindow ? 1 : (data.count || 0) + 1;
      if (nextCount > RATE_LIMIT_MAX_REQUESTS) {
        const blockedUntilMs = nowMs + RATE_LIMIT_BLOCK_MS;
        tx.set(docRef, {
          count: nextCount,
          windowStartMs,
          blockedUntilMs,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });
        return { allowed: false, retryAfterSec: 300, blockedUntilMs };
      }
      tx.set(docRef, {
        count: nextCount,
        windowStartMs,
        blockedUntilMs: 0,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      return { allowed: true, retryAfterSec: 0, blockedUntilMs: 0 };
    });
  } catch (_) {
    const existing = fallbackBuckets.get(safeKey);
    if (existing && nowMs < existing.blockedUntilMs) {
      const retryAfterSec = Math.max(1, Math.ceil((existing.blockedUntilMs - nowMs) / 1000));
      return { allowed: false, retryAfterSec, blockedUntilMs: existing.blockedUntilMs };
    }
    const isNewWindow =
      !existing ||
      (existing.blockedUntilMs > 0 && nowMs >= existing.blockedUntilMs) ||
      nowMs - existing.windowStartMs >= RATE_LIMIT_WINDOW_MS;
    const windowStartMs = isNewWindow ? nowMs : existing.windowStartMs;
    const nextCount = isNewWindow ? 1 : existing.count + 1;
    if (nextCount > RATE_LIMIT_MAX_REQUESTS) {
      const blockedUntilMs = nowMs + RATE_LIMIT_BLOCK_MS;
      fallbackBuckets.set(safeKey, { count: nextCount, windowStartMs, blockedUntilMs });
      return { allowed: false, retryAfterSec: 300, blockedUntilMs };
    }
    fallbackBuckets.set(safeKey, { count: nextCount, windowStartMs, blockedUntilMs: 0 });
    return { allowed: true, retryAfterSec: 0, blockedUntilMs: 0 };
  }
}

exports.streamOpenAiTutor = onRequest(
  {
    region: "asia-east1",
    secrets: [OPENAI_API_KEY],
    cors: [
      "https://gen-lang-client-0309263247.web.app",
      "https://gen-lang-client-0309263247.firebaseapp.com",
    ],
    timeoutSeconds: 120,
    memory: "256MiB",
  },
  async (req, res) => {
    applySecurityHeaders(res);

    if (req.method === "OPTIONS") {
      res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
      res.set(
        "Access-Control-Allow-Headers",
        "Content-Type, Authorization, X-Firebase-AppCheck, X-WCI-Portal-Role"
      );
      res.status(204).send("");
      return;
    }

    if (req.method !== "POST") {
      res.status(405).json({ error: "Method Not Allowed. Use POST." });
      return;
    }

    // 1. Verify Firebase App Check Token
    const appCheckToken = req.header("X-Firebase-AppCheck");
    if (!appCheckToken) {
      res.status(401).json({
        error: "Unauthorized: Missing X-Firebase-AppCheck attestation header.",
      });
      return;
    }

    let verifiedAppId = "verified_app";
    try {
      const verified = await admin.appCheck().verifyToken(appCheckToken);
      verifiedAppId = verified.appId || "verified_app";
    } catch (err) {
      res.status(401).json({
        error: "Unauthorized: Invalid or expired Firebase App Check token.",
      });
      return;
    }

    // 1b. Enforce Atomic Rate Limit (Max 5 requests per 60s window; 6th triggers 5-minute block)
    const clientIp = (req.headers["x-forwarded-for"] || req.ip || "ip_unknown").toString().split(",")[0].trim();
    const rateDecision = await checkAndConsumeServerRateLimit(`ai_sse:${verifiedAppId}:${clientIp}`);
    if (!rateDecision.allowed) {
      res.setHeader("Retry-After", String(rateDecision.retryAfterSec));
      res.status(429).json({
        error: `HTTP 429 Too Many Requests: Rate limit exceeded (max 5 requests per 60s). Blocked for ${rateDecision.retryAfterSec}s.`,
        retryAfterSeconds: rateDecision.retryAfterSec,
      });
      return;
    }

    // 2. Read OpenAI API Key exclusively from Cloud Secret Manager
    const apiKey = (OPENAI_API_KEY.value() || process.env.OPENAI_API_KEY || "").trim();
    if (!apiKey) {
      res.status(500).json({
        error: "Server configuration error: OPENAI_API_KEY secret is not configured.",
      });
      return;
    }

    // 3. Validate Request Payload
    const body = req.body || {};
    const systemPrompt = String(
      body.systemPrompt ||
        "You are Waqar Computer Institute AI, the official academic and operations assistant."
    ).slice(0, 6000);
    const userPrompt = String(body.userPrompt || "").trim().slice(0, 4000);
    const model = body.model === "gpt-4o" ? "gpt-4o" : "gpt-4o-mini";

    if (!userPrompt) {
      res.status(400).json({ error: "Missing userPrompt in request body." });
      return;
    }

    // 4. Configure Server-Sent Events (SSE) Response Headers
    res.setHeader("Content-Type", "text/event-stream; charset=utf-8");
    res.setHeader("Cache-Control", "no-cache, no-transform");
    res.setHeader("Connection", "keep-alive");
    res.setHeader("X-Accel-Buffering", "no");
    res.flushHeaders?.();

    try {
      const upstreamResponse = await fetch("https://api.openai.com/v1/chat/completions", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${apiKey}`,
        },
        body: JSON.stringify({
          model,
          stream: true,
          temperature: 0.4,
          max_tokens: 1200,
          messages: [
            { role: "system", content: systemPrompt },
            { role: "user", content: userPrompt },
          ],
        }),
      });

      if (!upstreamResponse.ok || !upstreamResponse.body) {
        const errText = await upstreamResponse.text().catch(() => "Upstream error");
        res.write(
          `data: ${JSON.stringify({ error: `OpenAI upstream status ${upstreamResponse.status}: ${errText.slice(0, 200)}` })}\n\n`
        );
        res.write("data: [DONE]\n\n");
        res.end();
        return;
      }

      const reader = upstreamResponse.body.getReader();
      const decoder = new TextDecoder("utf-8");
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() || "";

        for (const rawLine of lines) {
          const line = rawLine.trim();
          if (!line.startsWith("data:")) continue;
          const payload = line.slice(5).trim();
          if (payload === "[DONE]") {
            res.write("data: [DONE]\n\n");
            continue;
          }
          try {
            const parsed = JSON.parse(payload);
            const delta =
              parsed?.choices?.[0]?.delta?.content ||
              parsed?.choices?.[0]?.message?.content ||
              "";
            if (delta) {
              res.write(`data: ${JSON.stringify({ delta })}\n\n`);
            }
          } catch (_) {
            // Ignore partial JSON frame
          }
        }
      }

      res.write("data: [DONE]\n\n");
      res.end();
    } catch (err) {
      res.write(
        `data: ${JSON.stringify({ error: err.message || "Streaming proxy failure" })}\n\n`
      );
      res.write("data: [DONE]\n\n");
      res.end();
    }
  }
);
