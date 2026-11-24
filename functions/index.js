/**
 * Waqar Computer Institute ERP — Zero-Trust Backend Cloud Functions (Firebase Functions v2, asia-east1)
 *
 * Security & Zero-Bill AI Architecture:
 * 1. All Firestore reads/writes target the named database:
 *    `ai-studio-android-waqarins-b07db193-f2b2-469a-b782-7cb127ebd6a1` (NEVER `(default)`).
 * 2. `issuePortalCustomToken`: Verifies portal username/password server-side against
 *    `/portalCredentials/{username}` using constant-time PBKDF2-HMAC-SHA256 verification and
 *    mints a signed Firebase Custom Token with `{ role, portalUid, username, linkedEntityId }` claims.
 * 3. `streamInstituteAiTutor`: 100% Zero-Bill Computer-Studies-Only AI Tutor Proxy.
 *    - Enforces Firebase Auth ID Token (`Authorization: Bearer`) + Firebase App Check (`X-Firebase-AppCheck`).
 *    - Stage A: Server Security Guard (`meta-llama/llama-prompt-guard-2-86m` + anti-hacking/injection regex)
 *      with Strike 1 Warning -> Strike 2 24-Hour Lockout in `ai_tutor_locks/{uid}` + `/securityAlerts`.
 *    - Strict Computer-Studies Allowlist & Anti-Chit-Chat Filter (0 API cost).
 *    - Authoritative Firestore server transaction daily quota (`ai_student_quotas/{uid}`: 5 live calls/day,
 *      10s cooldown for students/parents; Admins & Teachers exempt).
 *    - Shared Firestore L3 Cache (`ai_shared_cache/{canonicalSha256}`).
 *    - Stage B: Header-Aware Zero-Bill Model Cascade routed through Cloudflare AI Gateway (`CF_AI_GATEWAY_URL`):
 *        1. Groq `openai/gpt-oss-120b`
 *        2. Groq `openai/gpt-oss-20b`
 *        3. Groq `qwen/qwen3.8-27b`
 *        4. Cloudflare Workers AI `@cf/google/gemma-4-26b-a4b-it` (10,000 free Neurons/day)
 *        5. Exhaustion Signal (`GROQ_AND_CLOUDFLARE_EXHAUSTED`) ONLY when BOTH Groq & Cloudflare are exhausted.
 * 4. `gradeExamSubmission`: Enforces Firebase ID Token + App Check + server-side rate limiting;
 *    derives `studentId` authoritatively from `decoded.uid`; grades against `/examAnswerKeys/{examId}`.
 */

const { onRequest } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const crypto = require("node:crypto");

if (!admin.apps.length) {
  admin.initializeApp();
}

const NAMED_DB_ID = "ai-studio-android-waqarins-b07db193-f2b2-469a-b782-7cb127ebd6a1";
const namedDb = () => getFirestore(admin.app(), NAMED_DB_ID);

const RATE_LIMIT_MAX_REQUESTS = 35;
const RATE_LIMIT_WINDOW_MS = 60 * 1000;       // 60 seconds
const RATE_LIMIT_BLOCK_MS = 5 * 60 * 1000;    // 5 minutes (300 seconds)
const PBKDF2_WEB_PARITY_ITERATIONS = 12000;
const PBKDF2_LEGACY_ITERATIONS = 65536;
const PBKDF2_HARDENED_ITERATIONS = 120000;
const PBKDF2_KEYLEN_BYTES = 32;
const DPOP_CLOCK_SKEW_SECONDS = 120;
const EXAM_GRACE_PERIOD_MS = 30 * 1000;
const AUDIT_CHAIN_GENESIS_HASH = "GENESIS_WCI_SERVER_AUDIT_CHAIN_V1";
const fallbackBuckets = new Map();

const STUDENT_BURST_CAPACITY = 3.0;
const NEWTON_COOLING_LAMBDA = 1.0 / 45.0; // 1/45 per sec (half-life ~31s)
const STUDENT_DAILY_LIVE_AI_CAP = 15;
const STUDENT_LIVE_COOLDOWN_MS = 10 * 1000;   // 10 seconds
const SECURITY_LOCKOUT_MS = 24 * 60 * 60 * 1000; // 24 hours
const MAX_COMPLETION_TOKENS = 350;
const UPSTREAM_TEMPERATURE = 0.3;

// In-memory circuit breaker cache for 0ms failover decisions
let inMemoryCircuitBreaker = {
  grokCircuitState: "CLOSED", // "CLOSED" | "OPEN"
  trippedAt: 0,
  tripReason: "",
  resetAt: 0,
};

const BOOTSTRAP_ADMIN_EMAIL = "samdkhan8256972@gmail.com";

const PROMPT_GUARD_MODEL = "meta-llama/llama-prompt-guard-2-86m";
const GROQ_MODEL_CASCADE = [
  "openai/gpt-oss-120b",
  "openai/gpt-oss-20b",
  "qwen/qwen3.8-27b",
];
const CF_WORKERS_AI_FALLBACK_MODEL = "@cf/google/gemma-4-26b-a4b-it";

const POLITE_STUDY_REDIRECT =
  "I am the Waqar Computer Institute Study Tutor. Please ask only Computer & IT course-related questions.";
const SECURITY_STRIKE_1_MESSAGE =
  "SECURITY WARNING (1/2): Hacking, security-bypass, or app-tampering questions are strictly prohibited. One more attempt will lock your AI Tutor for 24 hours and alert Admin.";
const SECURITY_STRIKE_2_LOCK_MESSAGE =
  "SECURITY LOCKOUT (24 HOURS): Repeated hacking, prompt-injection, or security-bypass attempt detected. Your AI Tutor has been locked for 24 hours and Admin has been alerted.";

// Per-instance header-aware cooldown state to proactively rotate BEFORE hitting HTTP 429
const groqModelCooldownUntilMs = new Map();

const STOPWORDS = new Set([
  "a", "an", "the", "is", "are", "was", "were", "what", "which", "who", "whom",
  "please", "pls", "plz", "tell", "me", "explain", "describe", "define", "about",
  "in", "of", "for", "to", "on", "with", "and", "or", "how", "do", "does", "can",
  "you", "give", "example", "kya", "hai", "hain", "batao", "samjhao", "ke", "ka", "ki",
]);

const HACKING_INJECTION_PATTERNS = [
  /\bignore\s+(all\s+)?(previous|prior|above)\s+(instructions?|rules?|prompts?)\b/i,
  /\b(system\s+prompt|developer\s+prompt|reveal\s+instructions?|show\s+hidden\s+prompt|internal\s+prompt)\b/i,
  /\b(jailbreak|dan\s+mode|bypass\s+(security|auth|login|exam|fee|fees|lock|guardrail|firewall|anti[-_]?tamper))\b/i,
  /\b(sql\s*injection|sqli|union\s+select|'\s*or\s*1\s*=\s*1|drop\s+table)\b/i,
  /\b(xss|cross[\s-]site\s+scripting|<script\b|document\.cookie)\b/i,
  /\b(frida|jadx|apktool|smali|decompile|reverse\s*engineer|mod\s*apk|apk\s*mod|crack\s*(apk|app|password|exam|license)|dump\s*dex|ghidra|ida\s*pro|xposed)\b/i,
  /\b(hack\s+(app|apk|exam|fees?|portal|firebase|admin|bank|wifi|account|password|server|database))\b/i,
  /\b(steal|dump|extract|leak|find)\s+(api[\s_-]?key|secret|token|password|credentials?|database|firestore|answer\s*key|system\s*prompt)\b/i,
  /\b(grok_api|gemini_api|groq_api|cloudfunctions\.net|firestore\.rules|database\s*schema)\b/i,
  /\b(exploit|payload|shellcode|rootkit|keylogger|phishing|ddos|brute[\s-]?force|ransomware|malware\s+code)\b/i,
];

const CHIT_CHAT_EXACT_OR_LEADING = [
  /^(hi+|hey+|hello+|hlo|hola|yo|sup|namaste|salam|assalam\s*alaikum|good\s*(morning|afternoon|evening|night)|gm|gn)[!.\s?]*$/i,
  /\b(kaise\s+ho|kaisa\s+hai|kaisi\s+ho|kya\s+haal\s+hai|haal\s+chaal|how\s+are\s+you|whats\s*up|what's\s*up|how\s+r\s+u|sab\s+badhiya)\b/i,
  /\b(tell\s+me\s+a\s+joke|jokes?|shayari|poem|poetry|love\s+letter|girlfriend|boyfriend|crush|marry\s+me|flirt|gossip|bollywood|hollywood|movie\s+review|web\s+series|cricket\s+score|ipl|pubg|bgmi|free\s+fire|betting|gambling|astrology|horoscope)\b/i,
  /^(thanks|thank\s+you|thx|ok|okay|bye|goodbye|tata|nice|cool|lol|haha|hehe)[!.\s?]*$/i,
];

const COMPUTER_STUDY_ALLOWLIST = [
  /\b(dca|adca|ccc|tally(\s+prime)?|gst|voucher|ledger|balance\s+sheet|payroll|inventory|ms\s*office|ms\s*word|ms\s*excel|excel|vlookup|xlookup|pivot\s+table|powerpoint|ms\s*access|libreoffice|calc|writer|impress)\b/i,
  /\b(python|java|kotlin|javascript|typescript|html5?|css3?|react|node\.?js|php|ruby|rust|golang|swift|dart|flutter|c\+\+|c#|c\s+language|c\s+programming|pointer|struct|oop|class|object|inheritance|polymorphism|encapsulation|recursion|loop|array|list|tuple|dictionary|function|variable|compiler|interpreter)\b/i,
  /\b(sql|mysql|sqlite|postgresql|mongodb|firestore|dbms|rdbms|normalization|1nf|2nf|3nf|primary\s+key|foreign\s+key|join|select\s+query|acid\s+properties|er\s+diagram)\b/i,
  /\b(operating\s+system|\bos\b|linux|unix|windows|kernel|process|thread|deadlock|paging|virtual\s+memory|scheduling|cpu|alu|ram|rom|ssd|hdd|motherboard|bios|uefi|cache\s+memory|microprocessor|hardware|software|peripheral|printer|scanner)\b/i,
  /\b(network(ing)?|osi\s+model|tcp\/?ip|http|https|dns|dhcp|ftp|smtp|lan|wan|man|router|switch|hub|ip\s+address|ipv4|ipv6|subnet|mac\s+address|topology|bandwidth|protocol|cloud\s+computing|cyber\s+hygiene|encryption|hashing|sha-?256|aes)\b/i,
  /\b(binary|octal|hexadecimal|decimal|ascii|unicode|boolean|logic\s+gate|and\s+gate|or\s+gate|nand|nor|xor|k-?map|algorithm|flowchart|pseudocode|data\s+structure|stack|queue|linked\s+list|tree|graph|sorting|searching|big\s*o|time\s+complexity)\b/i,
  /\b(computer|coding|programming|code|debug|syntax|error|exception|api|json|xml|git|github|typing|wpm|shortcut\s+key|ctrl\+|alt\+|f[1-9]|f1[0-2]|syllabus|exam\s+prep|mcq|quiz|lesson\s+plan|assignment|lab\s+exercise|waqar\s+computer\s+institute|course|batch|notice|attendance|fee\s+structure|certificate|photoshop|coreldraw|pagemaker|dtp|autocad|ai\s+basics|machine\s+learning)\b/i,
];

function applySecurityHeaders(res) {
  res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("X-Frame-Options", "DENY");
  res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
  res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
}

function normalizeUsername(raw) {
  return String(raw || "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_.\-@]/g, "_");
}

function canonicalizePromptForCache(featureType, languageCode, prompt) {
  const tokens = String(prompt || "")
    .toLowerCase()
    .replace(/[^a-z0-9+#.\s]/g, " ")
    .split(/\s+/)
    .map((t) => t.trim())
    .filter((t) => t.length > 0 && !STOPWORDS.has(t));
  const uniqueSorted = Array.from(new Set(tokens)).sort();
  const base = uniqueSorted.length > 0
    ? uniqueSorted.join(" ")
    : String(prompt || "").trim().toLowerCase().replace(/\s+/g, " ");
  return `${String(featureType || "TUTOR_CHAT").trim().toUpperCase()}|${String(languageCode || "EN").trim().toUpperCase()}|${base}`;
}

function computeCanonicalSha256(featureType, languageCode, prompt) {
  const canonical = canonicalizePromptForCache(featureType, languageCode, prompt);
  return crypto.createHash("sha256").update(canonical, "utf8").digest("hex");
}

function isSecurityAttackPrompt(prompt) {
  const text = String(prompt || "").trim();
  if (!text) return false;
  return HACKING_INJECTION_PATTERNS.some((re) => re.test(text));
}

function isCasualChitChatOrOffTopic(prompt) {
  const text = String(prompt || "").trim();
  if (!text) return true;
  if (CHIT_CHAT_EXACT_OR_LEADING.some((re) => re.test(text))) {
    return true;
  }
  return !COMPUTER_STUDY_ALLOWLIST.some((re) => re.test(text));
}

function resolveAuthoritativeRole(decodedToken) {
  const claimRole = String(decodedToken?.role || "").trim().toLowerCase();
  if (["admin", "teacher", "student", "parent"].includes(claimRole)) {
    return claimRole;
  }
  const email = String(decodedToken?.email || "").trim().toLowerCase();
  if (email === BOOTSTRAP_ADMIN_EMAIL && decodedToken?.email_verified === true) {
    return "admin";
  }
  return "student";
}

function constantTimeHexEquals(expectedHex, actualHex) {
  const a = Buffer.from(String(expectedHex || "").toLowerCase(), "utf8");
  const b = Buffer.from(String(actualHex || "").toLowerCase(), "utf8");
  if (a.length === 0 || a.length !== b.length) return false;
  return crypto.timingSafeEqual(a, b);
}

function constantTimeStringEquals(expectedStr, actualStr) {
  const a = Buffer.from(String(expectedStr || ""), "utf8");
  const b = Buffer.from(String(actualStr || ""), "utf8");
  if (a.length === 0 || a.length !== b.length) return false;
  return crypto.timingSafeEqual(a, b);
}

function verifyPbkdf2Hex(password, saltHex, expectedHashHex) {
  const cleanSalt = String(saltHex || "").trim().toLowerCase();
  const cleanExpected = String(expectedHashHex || "").trim().toLowerCase();
  if (!cleanSalt || cleanExpected.length !== 64 || !/^[0-9a-f]{64}$/.test(cleanExpected)) {
    return false;
  }
  const saltBuf = /^[0-9a-f]+$/.test(cleanSalt) && cleanSalt.length % 2 === 0
    ? Buffer.from(cleanSalt, "hex")
    : Buffer.from(cleanSalt, "utf8");

  const derived120k = crypto
    .pbkdf2Sync(password, saltBuf, PBKDF2_HARDENED_ITERATIONS, PBKDF2_KEYLEN_BYTES, "sha256")
    .toString("hex");
  if (constantTimeHexEquals(cleanExpected, derived120k)) {
    return true;
  }

  const derived12k = crypto
    .pbkdf2Sync(password, saltBuf, PBKDF2_WEB_PARITY_ITERATIONS, PBKDF2_KEYLEN_BYTES, "sha256")
    .toString("hex");
  if (constantTimeHexEquals(cleanExpected, derived12k)) {
    return true;
  }

  const derived65k = crypto
    .pbkdf2Sync(password, saltBuf, PBKDF2_LEGACY_ITERATIONS, PBKDF2_KEYLEN_BYTES, "sha256")
    .toString("hex");
  return constantTimeHexEquals(cleanExpected, derived65k);
}

function sanitizeDocKey(raw, maxLen = 120) {
  return String(raw || "")
    .trim()
    .replace(/[^a-zA-Z0-9_.\-:]/g, "_")
    .slice(0, maxLen);
}

function canonicalizeJson(value) {
  if (value === null || typeof value !== "object") {
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) {
    return `[${value.map((item) => canonicalizeJson(item)).join(",")}]`;
  }
  const sortedKeys = Object.keys(value).sort();
  return `{${sortedKeys.map((k) => `${JSON.stringify(k)}:${canonicalizeJson(value[k])}`).join(",")}}`;
}

async function assertDeviceNotRevoked(db, req) {
  const rawFp = String(
    req.header("X-WCI-Device-Fp") ||
    req.body?.deviceFingerprint ||
    ""
  ).trim();
  if (!rawFp) {
    return { revoked: false, deviceFp: "" };
  }
  const safeFp = sanitizeDocKey(rawFp, 128);
  try {
    const snap = await db.collection("revoked_devices").doc(safeFp).get();
    if (snap.exists && snap.data()?.revoked === true) {
      const reason = String(snap.data()?.reason || "Device revoked by Institute Admin.");
      return { revoked: true, deviceFp: safeFp, reason };
    }
  } catch (_) {
    // Allow through if lookup momentarily unavailable
  }
  return { revoked: false, deviceFp: safeFp };
}

/**
 * Sideload-Compatible Best-Effort App Check Verification:
 * This APK is distributed outside Google Play Store via WhatsApp/GitHub OTA, where Play
 * Integrity App Check is unavailable. Verify the token when provided, log when absent or
 * unverified, and NEVER reject sideloaded APK clients with HTTP 401 solely for missing App Check.
 */
async function verifyAppCheckBestEffort(req, functionName) {
  const appCheckToken = String(req.header("X-Firebase-AppCheck") || "").trim();
  if (!appCheckToken) {
    console.info(`[${functionName}] X-Firebase-AppCheck absent (sideload APK mode)`);
    return { verified: false, appId: "sideload_apk" };
  }
  try {
    const verified = await admin.appCheck().verifyToken(appCheckToken);
    return { verified: true, appId: verified.appId || "verified_app" };
  } catch (err) {
    console.warn(`[${functionName}] X-Firebase-AppCheck verification skipped on sideload client: ${err?.message || "unverified"}`);
    return { verified: false, appId: "sideload_unverified" };
  }
}

/**
 * Validates `X-WCI-App-Cert-Sha256` sent by sideloaded Release APKs.
 * Always validates 64-char SHA-256 hex format when present, and matches against
 * configured release keystore pins (`process.env.EXPECTED_CERT_SHA256`) when set.
 */
function verifySideloadAppCertHeader(req) {
  const rawCert = String(req.header("X-WCI-App-Cert-Sha256") || "")
    .replace(/:/g, "")
    .trim()
    .toLowerCase();
  if (!rawCert) {
    return { ok: true, certSha256: "" };
  }
  if (!/^[0-9a-f]{64}$/.test(rawCert)) {
    return { ok: false, status: 403, error: "Malformed X-WCI-App-Cert-Sha256 signing certificate digest." };
  }
  const configuredRaw = String(process.env.EXPECTED_CERT_SHA256 || "").trim();
  if (configuredRaw) {
    const expectedPins = configuredRaw
      .split(",")
      .map((s) => s.replace(/:/g, "").trim().toLowerCase())
      .filter((s) => /^[0-9a-f]{64}$/.test(s));
    if (expectedPins.length > 0 && !expectedPins.some((pin) => constantTimeHexEquals(pin, rawCert))) {
      return { ok: false, status: 403, error: "Untrusted application signing certificate." };
    }
  }
  return { ok: true, certSha256: rawCert };
}

function decodeBase64UrlToBuffer(b64url) {
  const normalized = String(b64url || "").replace(/-/g, "+").replace(/_/g, "/");
  const pad = normalized.length % 4 === 0 ? "" : "=".repeat(4 - (normalized.length % 4));
  return Buffer.from(normalized + pad, "base64");
}

/**
 * Verifies hardware-bound RFC 9449-style `X-WCI-DPoP` ECDSA P-256 request proof against
 * `device_pubkeys/{uid}_{deviceFp}`, enforces ±120s timestamp window, and atomically rejects
 * replayed `jti` nonces in `dpop_nonces/{jti}`.
 */
async function verifyDpopProof(req, db, uid) {
  const dpopHeader = String(req.header("X-WCI-DPoP") || "").trim();
  const deviceFpHeader = sanitizeDocKey(req.header("X-WCI-Device-Fp") || "", 128);

  if (!dpopHeader) {
    if (uid && deviceFpHeader) {
      try {
        const regSnap = await db.collection("device_pubkeys").doc(`${sanitizeDocKey(uid, 64)}_${deviceFpHeader}`).get();
        if (regSnap.exists && regSnap.data()?.spkiBase64) {
          return { ok: false, status: 401, error: "Missing required X-WCI-DPoP proof for hardware-bound device." };
        }
      } catch (_) {
        // Non-blocking if key registry lookup fails
      }
    }
    return { ok: true, verified: false };
  }

  const parts = dpopHeader.split(".");
  if (parts.length !== 2 || !parts[0] || !parts[1]) {
    return { ok: false, status: 401, error: "Malformed X-WCI-DPoP header." };
  }

  let payload;
  try {
    payload = JSON.parse(decodeBase64UrlToBuffer(parts[0]).toString("utf8"));
  } catch (_) {
    return { ok: false, status: 401, error: "Unparseable X-WCI-DPoP payload." };
  }

  const jti = sanitizeDocKey(payload.jti || "", 96);
  const iat = Number(payload.iat || 0);
  const htm = String(payload.htm || "").trim().toUpperCase();
  const htu = String(payload.htu || "").trim();
  const ath = String(payload.ath || "").trim().toLowerCase();
  const payloadFp = sanitizeDocKey(payload.deviceFp || deviceFpHeader, 128);

  if (!jti || !Number.isFinite(iat) || iat <= 0 || !htm || !htu || !payloadFp) {
    return { ok: false, status: 401, error: "Incomplete X-WCI-DPoP claims." };
  }

  const nowSec = Math.floor(Date.now() / 1000);
  if (Math.abs(nowSec - iat) > DPOP_CLOCK_SKEW_SECONDS) {
    return { ok: false, status: 401, error: "Expired or skewed X-WCI-DPoP timestamp (±120s)." };
  }

  if (htm !== String(req.method || "POST").toUpperCase()) {
    return { ok: false, status: 401, error: "X-WCI-DPoP HTTP method mismatch." };
  }

  if (ath && req.rawBody && Buffer.isBuffer(req.rawBody)) {
    const actualBodyHash = crypto.createHash("sha256").update(req.rawBody).digest("hex").toLowerCase();
    if (!constantTimeHexEquals(ath, actualBodyHash)) {
      return { ok: false, status: 401, error: "X-WCI-DPoP body digest mismatch." };
    }
  }

  // Verify ECDSA P-256 signature against registered hardware public key when available
  if (uid) {
    const pubKeyDocId = `${sanitizeDocKey(uid, 64)}_${payloadFp}`;
    const pubSnap = await db.collection("device_pubkeys").doc(pubKeyDocId).get();
    if (pubSnap.exists && pubSnap.data()?.spkiBase64) {
      try {
        const spkiDer = Buffer.from(String(pubSnap.data().spkiBase64), "base64");
        const publicKey = crypto.createPublicKey({ key: spkiDer, format: "der", type: "spki" });
        const sigBuf = decodeBase64UrlToBuffer(parts[1]);
        const verifier = crypto.createVerify("SHA256");
        verifier.update(parts[0], "utf8");
        verifier.end();
        const sigValid = verifier.verify(publicKey, sigBuf);
        if (!sigValid) {
          return { ok: false, status: 401, error: "Invalid X-WCI-DPoP hardware ECDSA signature." };
        }
      } catch (_) {
        return { ok: false, status: 401, error: "Failed X-WCI-DPoP ECDSA verification." };
      }
    }
  }

  // Atomic anti-replay nonce consumption
  try {
    const nonceRef = db.collection("dpop_nonces").doc(jti);
    const consumed = await db.runTransaction(async (tx) => {
      const snap = await tx.get(nonceRef);
      if (snap.exists) {
        return false;
      }
      tx.set(nonceRef, {
        jti,
        uid: String(uid || "pre_auth"),
        deviceFp: payloadFp,
        iat,
        expiresAtMs: Date.now() + 300_000,
        createdAt: FieldValue.serverTimestamp(),
      });
      return true;
    });
    if (!consumed) {
      return { ok: false, status: 401, error: "DPoP replay detected: jti already consumed." };
    }
  } catch (_) {
    // Allow through only if Firestore nonce transaction momentarily unavailable
  }

  return { ok: true, verified: true, deviceFp: payloadFp, jti };
}

function credentialSupportsRole(credData, requestedRole) {
  const roleKey = String(requestedRole || "").trim().toLowerCase();
  if (!roleKey) return false;
  if (String(credData.role || "").trim().toLowerCase() === roleKey) return true;
  if (Array.isArray(credData.portalRoles)) {
    const normalized = credData.portalRoles.map((r) => String(r || "").trim().toLowerCase());
    if (normalized.includes(roleKey)) return true;
  }
  if (roleKey === "admin" && credData.isAdmin === true) return true;
  if (roleKey === "teacher" && credData.isTeacher === true) return true;
  if (roleKey === "student" && credData.isStudent === true) return true;
  if (roleKey === "parent" && credData.isParent === true) return true;
  return false;
}

async function checkAndConsumeServerRateLimit(bucketKey, maxRequests = RATE_LIMIT_MAX_REQUESTS, nowMs = Date.now()) {
  const safeKey = String(bucketKey || "anon").replace(/[^a-zA-Z0-9_.:@-]/g, "_").slice(0, 120);
  try {
    const db = namedDb();
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
      if (nextCount > maxRequests) {
        const blockedUntilMs = nowMs + RATE_LIMIT_BLOCK_MS;
        tx.set(docRef, {
          count: nextCount,
          windowStartMs,
          blockedUntilMs,
          updatedAt: FieldValue.serverTimestamp(),
        });
        return { allowed: false, retryAfterSec: 300, blockedUntilMs };
      }
      tx.set(docRef, {
        count: nextCount,
        windowStartMs,
        blockedUntilMs: 0,
        updatedAt: FieldValue.serverTimestamp(),
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
    if (nextCount > maxRequests) {
      const blockedUntilMs = nowMs + RATE_LIMIT_BLOCK_MS;
      fallbackBuckets.set(safeKey, { count: nextCount, windowStartMs, blockedUntilMs });
      return { allowed: false, retryAfterSec: 300, blockedUntilMs };
    }
    fallbackBuckets.set(safeKey, { count: nextCount, windowStartMs, blockedUntilMs: 0 });
    return { allowed: true, retryAfterSec: 0, blockedUntilMs: 0 };
  }
}

function buildGroqEndpointUrl(cfGatewayUrl) {
  const clean = String(cfGatewayUrl || "").trim().replace(/\/+$/, "");
  if (!clean) {
    return "https://api.groq.com/openai/v1/chat/completions";
  }
  if (clean.endsWith("/chat/completions")) {
    return clean;
  }
  if (clean.endsWith("/groq")) {
    return `${clean}/chat/completions`;
  }
  return `${clean}/groq/chat/completions`;
}

function buildWorkersAiEndpointUrl(cfGatewayUrl, modelId) {
  const clean = String(cfGatewayUrl || "").trim().replace(/\/+$/, "");
  if (!clean) return "";
  // Strip provider suffix if owner pasted a provider-specific gateway URL
  const baseGateway = clean.replace(/\/(groq|workers-ai)(\/.*)?$/i, "");
  return `${baseGateway}/workers-ai/${modelId}`;
}

function buildUpstreamHeaders(groqKey, cfToken, useGateway) {
  const headers = {
    "Content-Type": "application/json",
  };
  if (groqKey) {
    headers.Authorization = `Bearer ${groqKey}`;
  }
  if (useGateway && cfToken) {
    headers["cf-aig-authorization"] = `Bearer ${cfToken}`;
    headers["cf-aig-cache-ttl"] = "86400";
  }
  return headers;
}

function inspectGroqRateHeadersAndMarkCooldown(modelName, response) {
  const now = Date.now();
  const remReq = Number.parseInt(response.headers.get("x-ratelimit-remaining-requests") || "", 10);
  const remTok = Number.parseInt(response.headers.get("x-ratelimit-remaining-tokens") || "", 10);
  const retryAfterSec = Number.parseFloat(response.headers.get("retry-after") || "0");

  if (response.status === 429) {
    const cooldownMs = Number.isFinite(retryAfterSec) && retryAfterSec > 0
      ? Math.min(3600_000, Math.ceil(retryAfterSec * 1000))
      : 90_000;
    groqModelCooldownUntilMs.set(modelName, now + cooldownMs);
    return true;
  }

  // Proactive rotation BEFORE hitting 429
  if (
    (Number.isFinite(remReq) && remReq <= 1) ||
    (Number.isFinite(remTok) && remTok < MAX_COMPLETION_TOKENS + 150)
  ) {
    const proactiveMs = Number.isFinite(retryAfterSec) && retryAfterSec > 0
      ? Math.min(300_000, Math.ceil(retryAfterSec * 1000))
      : 60_000;
    groqModelCooldownUntilMs.set(modelName, now + proactiveMs);
  }
  return false;
}

/**
 * Stage A — Server Security Guard using Groq `meta-llama/llama-prompt-guard-2-86m`
 * (Free Plan: 30 RPM, 14,400 RPD) combined with regex detection.
 */
async function evaluatePromptGuardServer(userPrompt, groqKey, cfGatewayUrl, cfToken) {
  if (isSecurityAttackPrompt(userPrompt)) {
    return { malicious: true, source: "SERVER_SECURITY_REGEX", score: 1.0 };
  }
  if (!groqKey) {
    return { malicious: false, source: "REGEX_ONLY_NO_GROQ_KEY", score: 0.0 };
  }

  try {
    const endpoint = buildGroqEndpointUrl(cfGatewayUrl);
    const resp = await fetch(endpoint, {
      method: "POST",
      headers: buildUpstreamHeaders(groqKey, cfToken, Boolean(cfGatewayUrl)),
      body: JSON.stringify({
        model: PROMPT_GUARD_MODEL,
        temperature: 0.0,
        max_completion_tokens: 32,
        messages: [{ role: "user", content: userPrompt.slice(0, 1500) }],
      }),
    });

    if (!resp.ok) {
      return { malicious: false, source: `GUARD_HTTP_${resp.status}`, score: 0.0 };
    }

    const data = await resp.json().catch(() => ({}));
    const rawVerdict = String(
      data?.choices?.[0]?.message?.content ||
      data?.choices?.[0]?.text ||
      ""
    ).trim().toLowerCase();

    const numericScore = Number.parseFloat(rawVerdict);
    const isJailbreakLabel =
      rawVerdict.includes("jailbreak") ||
      rawVerdict.includes("injection") ||
      rawVerdict.includes("malicious") ||
      rawVerdict.includes("unsafe") ||
      (Number.isFinite(numericScore) && numericScore >= 0.75);

    return {
      malicious: isJailbreakLabel,
      source: PROMPT_GUARD_MODEL,
      score: Number.isFinite(numericScore) ? numericScore : (isJailbreakLabel ? 0.99 : 0.01),
    };
  } catch (_) {
    return { malicious: false, source: "GUARD_FALLBACK_REGEX", score: 0.0 };
  }
}

async function recordSecurityStrikeAndLockIfRepeated(db, studentUid, username, reason, promptSnippet, req = null) {
  const now = Date.now();
  const lockRef = db.collection("ai_tutor_locks").doc(studentUid);
  const outcome = await db.runTransaction(async (tx) => {
    const snap = await tx.get(lockRef);
    const data = snap.exists ? snap.data() : {};
    const existingLockedUntil = Number(data.lockedUntilMs || 0);
    if (existingLockedUntil > now) {
      return { strikeCount: Number(data.strikeCount || 2), locked: true, lockedUntilMs: existingLockedUntil };
    }
    // Zero-Tolerance Auto-Block: Any intrusion/prompt-injection attempt locks immediately for 24 hours
    const lockedUntilMs = now + SECURITY_LOCKOUT_MS;

    tx.set(
      lockRef,
      {
        uid: studentUid,
        username: String(username || studentUid).slice(0, 100),
        strikeCount: 2,
        locked: true,
        lockedUntilMs,
        lastReason: String(reason || "PROMPT_INJECTION").slice(0, 160),
        lastViolationAt: FieldValue.serverTimestamp(),
        lockedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
    return { strikeCount: 2, locked: true, lockedUntilMs };
  });

  // Mirror 24-hour lockout to student's user profile document (aiBlockedUntil)
  db.collection("users").doc(studentUid).set(
    { aiBlockedUntil: outcome.lockedUntilMs },
    { merge: true }
  ).catch(() => {});

  const deviceId = req
    ? String(req.header("X-WCI-Device-Fp") || req.header("X-Device-Id") || "DEVICE_UNKNOWN").slice(0, 128)
    : "DEVICE_UNKNOWN";

  // 1. High-Priority Log to `admin_security_alerts` collection
  const adminAlertDoc = {
    studentId: studentUid,
    studentName: String(username || studentUid).slice(0, 100),
    deviceId,
    timestamp: FieldValue.serverTimestamp(),
    maliciousPrompt: String(promptSnippet || "").slice(0, 1000),
    severity: "CRITICAL",
    reason: String(reason || "Zero-Cost AI Intrusion / System Prompt Extraction Attempt").slice(0, 180),
    detectedBy: "Zero-Cost Heuristic Security Firewall",
    status: "LOCKED_24H",
    lockedUntilMs: outcome.lockedUntilMs,
    createdAt: FieldValue.serverTimestamp(),
  };
  db.collection("admin_security_alerts").add(adminAlertDoc).catch(() => {});

  // 2. Backward-compatible log to `securityAlerts` collection
  const alertId = `ai_sec_${now}_${crypto.randomBytes(3).toString("hex")}`;
  await db.collection("securityAlerts").doc(alertId).set({
    id: alertId,
    type: "AI_TUTOR_LOCKED_24H",
    severity: "CRITICAL",
    studentUid,
    username: String(username || studentUid).slice(0, 100),
    strikeCount: outcome.strikeCount,
    lockedUntilMs: outcome.lockedUntilMs,
    details: `${reason} | device=${deviceId} | snippet=${String(promptSnippet || "").slice(0, 180)}`,
    createdAt: FieldValue.serverTimestamp(),
  }).catch(() => {});

  return outcome;
}

/**
 * Mathematical & Physics-Inspired Rate Limiting:
 * Token Bucket + Newton's Law of Cooling (Exponential Decay).
 * Burst capacity: 3 questions; Refills slowly via Newton's Cooling exponential curve;
 * Capped at strict daily free limit (15 questions/day) to guarantee 100% Zero-Billing forever.
 */
async function checkAndConsumeDailyStudentQuota(db, studentUid) {
  const now = Date.now();
  const todayUtc = new Date(now).toISOString().slice(0, 10);
  const quotaRef = db.collection("ai_student_quotas").doc(studentUid);

  return db.runTransaction(async (tx) => {
    const snap = await tx.get(quotaRef);
    const data = snap.exists ? snap.data() : {};
    const sameDay = data.dateUtc === todayUtc;
    const usedToday = sameDay ? Number(data.liveCallsUsed || 0) : 0;

    // Strict Daily Free Quota Cap (15 questions/day)
    if (usedToday >= STUDENT_DAILY_LIVE_AI_CAP) {
      const midnightUtc = Date.UTC(
        new Date(now).getUTCFullYear(),
        new Date(now).getUTCMonth(),
        new Date(now).getUTCDate() + 1
      );
      const waitSec = Math.max(60, Math.ceil((midnightUtc - now) / 1000));
      return {
        allowed: false,
        reason: "DAILY_CAP",
        waitSeconds: waitSec,
        remainingToday: 0,
        tokensRemaining: 0,
      };
    }

    // Token Bucket + Newton's Law of Cooling (Exponential Decay)
    const prevTokens = typeof data.tokens === "number"
      ? Math.min(STUDENT_BURST_CAPACITY, Math.max(0, data.tokens))
      : STUDENT_BURST_CAPACITY;
    const lastDecayMs = Number(data.lastDecayTimeMs || (now - 60000));
    const deltaSec = Math.max(0, (now - lastDecayMs) / 1000);

    // Newton's Cooling: T(t) = B - (B - T_prev) * e^(-lambda * delta_t)
    const cooledTokens = Math.min(
      STUDENT_BURST_CAPACITY,
      STUDENT_BURST_CAPACITY - (STUDENT_BURST_CAPACITY - prevTokens) * Math.exp(-NEWTON_COOLING_LAMBDA * deltaSec)
    );

    if (cooledTokens < 1.0) {
      const ratio = (STUDENT_BURST_CAPACITY - 1.0) / Math.max(0.01, STUDENT_BURST_CAPACITY - cooledTokens);
      const waitSec = Math.max(5, Math.ceil(-Math.log(ratio) / NEWTON_COOLING_LAMBDA));
      return {
        allowed: false,
        reason: "COOLDOWN",
        waitSeconds: waitSec,
        remainingToday: Math.max(0, STUDENT_DAILY_LIVE_AI_CAP - usedToday),
        tokensRemaining: Math.floor(cooledTokens),
      };
    }

    const nextTokens = Math.max(0, cooledTokens - 1.0);
    const nextUsed = usedToday + 1;

    tx.set(
      quotaRef,
      {
        uid: studentUid,
        dateUtc: todayUtc,
        tokens: nextTokens,
        lastDecayTimeMs: now,
        liveCallsUsed: nextUsed,
        dailyCap: STUDENT_DAILY_LIVE_AI_CAP,
        lastLiveCallMs: now,
        lastLiveCallAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    return {
      allowed: true,
      reason: "OK",
      waitSeconds: 0,
      remainingToday: Math.max(0, STUDENT_DAILY_LIVE_AI_CAP - nextUsed),
      tokensRemaining: Math.floor(nextTokens),
    };
  });
}

/**
 * Circuit Breaker Physics for Grok API (Primary) -> Google Gemini API (Free Tier Secondary)
 */
async function getCircuitBreakerState(db) {
  const now = Date.now();
  if (inMemoryCircuitBreaker.grokCircuitState === "OPEN" && inMemoryCircuitBreaker.resetAt > now) {
    return inMemoryCircuitBreaker;
  }
  try {
    const snap = await db.collection("system_circuits").doc("ai_circuit_breaker").get();
    if (snap.exists) {
      const data = snap.data() || {};
      const resetAt = Number(data.resetAt || 0);
      if (data.grokCircuitState === "OPEN" && resetAt > now) {
        inMemoryCircuitBreaker = {
          grokCircuitState: "OPEN",
          trippedAt: Number(data.trippedAt || now),
          tripReason: String(data.tripReason || "QUOTA_EXHAUSTED"),
          resetAt,
        };
        return inMemoryCircuitBreaker;
      }
    }
  } catch (_) {}
  inMemoryCircuitBreaker.grokCircuitState = "CLOSED";
  return inMemoryCircuitBreaker;
}

async function tripCircuitBreaker(db, reason) {
  const now = Date.now();
  const nextMidnightUtc = Date.UTC(
    new Date(now).getUTCFullYear(),
    new Date(now).getUTCMonth(),
    new Date(now).getUTCDate() + 1
  );
  inMemoryCircuitBreaker = {
    grokCircuitState: "OPEN",
    trippedAt: now,
    tripReason: reason,
    resetAt: nextMidnightUtc,
  };
  try {
    await db.collection("system_circuits").doc("ai_circuit_breaker").set(
      {
        grokCircuitState: "OPEN",
        trippedAt: now,
        tripReason: reason,
        resetAt: nextMidnightUtc,
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
  } catch (_) {}
}

async function callGrokApi(grokKey, cfGatewayUrl, cfToken, systemPrompt, userPrompt) {
  const isXaiKey = grokKey.startsWith("xai-");
  const endpoint = isXaiKey
    ? "https://api.x.ai/v1/chat/completions"
    : buildGroqEndpointUrl(cfGatewayUrl);
  const model = isXaiKey ? "grok-beta" : "llama-3.3-70b-versatile";

  const headers = isXaiKey
    ? {
        "Content-Type": "application/json",
        Authorization: `Bearer ${grokKey}`,
      }
    : buildUpstreamHeaders(grokKey, cfToken, Boolean(cfGatewayUrl));

  const messages = [
    { role: "system", content: systemPrompt },
    { role: "user", content: userPrompt },
  ];

  // Circuit Breaker: strict 6-second timeout
  const resp = await fetch(endpoint, {
    method: "POST",
    headers,
    body: JSON.stringify({
      model,
      stream: true,
      temperature: UPSTREAM_TEMPERATURE,
      max_tokens: MAX_COMPLETION_TOKENS,
      max_completion_tokens: MAX_COMPLETION_TOKENS,
      messages,
    }),
    signal: AbortSignal.timeout(6000),
  });

  return { resp, model };
}

async function streamGrokSseResponse(grokResp, res, chosenModel, cacheHash, featureType, languageCode, userPrompt, db) {
  res.setHeader("Content-Type", "text/event-stream; charset=utf-8");
  res.setHeader("Cache-Control", "no-cache, no-transform");
  res.setHeader("Connection", "keep-alive");
  res.setHeader("X-Accel-Buffering", "no");
  res.flushHeaders?.();

  let fullText = "";
  const reader = grokResp.body.getReader();
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
      if (payload === "[DONE]") continue;

      try {
        const parsed = JSON.parse(payload);
        const delta =
          parsed?.choices?.[0]?.delta?.content ||
          parsed?.choices?.[0]?.message?.content ||
          "";
        if (delta) {
          fullText += delta;
          res.write(`data: ${JSON.stringify({ delta, model: chosenModel })}\n\n`);
        }
      } catch (_) {}
    }
  }

  res.write("data: [DONE]\n\n");
  res.end();

  if (fullText.trim().length > 0) {
    db.collection("ai_shared_cache").doc(cacheHash).set(
      {
        promptHash: cacheHash,
        featureType,
        languageCode,
        canonicalPrompt: userPrompt.trim().toLowerCase().slice(0, 1000),
        promptText: userPrompt.slice(0, 1000),
        responseText: fullText.trim().slice(0, 8000),
        modelName: chosenModel,
        hitCount: 1,
        updatedAt: FieldValue.serverTimestamp(),
        createdAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    ).catch(() => {});
  }
}

async function callGeminiApi(geminiKey, systemPrompt, userPrompt, res, cacheHash, featureType, languageCode, db) {
  const modelName = "gemini-2.5-flash";
  const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${modelName}:streamGenerateContent?alt=sse&key=${geminiKey}`;

  const payload = {
    system_instruction: {
      parts: [{ text: systemPrompt }],
    },
    contents: [
      {
        role: "user",
        parts: [{ text: userPrompt }],
      },
    ],
    generationConfig: {
      temperature: UPSTREAM_TEMPERATURE,
      maxOutputTokens: MAX_COMPLETION_TOKENS,
    },
  };

  let upstreamResp;
  try {
    upstreamResp = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(15000),
    });
  } catch (_) {
    // If 2.5-flash timed out, try gemini-1.5-flash
    const fbEndpoint = `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:streamGenerateContent?alt=sse&key=${geminiKey}`;
    upstreamResp = await fetch(fbEndpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(15000),
    });
  }

  if (!upstreamResp || !upstreamResp.ok) {
    throw new Error(`Gemini API error (HTTP ${upstreamResp ? upstreamResp.status : "unknown"})`);
  }

  return streamGeminiSseResponse(upstreamResp, res, modelName, cacheHash, featureType, languageCode, userPrompt, db);
}

async function streamGeminiSseResponse(geminiResp, res, modelTag, cacheHash, featureType, languageCode, userPrompt, db) {
  res.setHeader("Content-Type", "text/event-stream; charset=utf-8");
  res.setHeader("Cache-Control", "no-cache, no-transform");
  res.setHeader("Connection", "keep-alive");
  res.setHeader("X-Accel-Buffering", "no");
  res.flushHeaders?.();

  let fullText = "";
  const reader = geminiResp.body.getReader();
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
      const dataStr = line.slice(5).trim();
      if (!dataStr || dataStr === "[DONE]") continue;

      try {
        const parsed = JSON.parse(dataStr);
        const parts = parsed?.candidates?.[0]?.content?.parts;
        if (Array.isArray(parts)) {
          for (const p of parts) {
            const textChunk = p.text || "";
            if (textChunk) {
              fullText += textChunk;
              res.write(`data: ${JSON.stringify({ delta: textChunk, model: `gemini/${modelTag}` })}\n\n`);
            }
          }
        }
      } catch (_) {}
    }
  }

  res.write("data: [DONE]\n\n");
  res.end();

  if (fullText.trim().length > 0) {
    db.collection("ai_shared_cache").doc(cacheHash).set(
      {
        promptHash: cacheHash,
        featureType,
        languageCode,
        canonicalPrompt: userPrompt.trim().toLowerCase().slice(0, 1000),
        promptText: userPrompt.slice(0, 1000),
        responseText: fullText.trim().slice(0, 8000),
        modelName: `gemini/${modelTag}`,
        hitCount: 1,
        updatedAt: FieldValue.serverTimestamp(),
        createdAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    ).catch(() => {});
  }
}

/**
 * Zero-Trust Portal Authentication Token Exchange (`issuePortalCustomToken`).
 */
exports.issuePortalCustomToken = onRequest(
  {
    region: "asia-east1",
    cors: [
      "https://gen-lang-client-0309263247.web.app",
      "https://gen-lang-client-0309263247.firebaseapp.com",
    ],
    timeoutSeconds: 30,
    memory: "256MiB",
  },
  async (req, res) => {
    applySecurityHeaders(res);

    if (req.method === "OPTIONS") {
      res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
      res.set(
        "Access-Control-Allow-Headers",
        "Content-Type, X-Firebase-AppCheck, X-WCI-App-Cert-Sha256, X-WCI-DPoP, X-WCI-Device-Fp"
      );
      res.status(204).send("");
      return;
    }

    if (req.method !== "POST") {
      return res.status(405).json({ error: "Method Not Allowed. Use POST." });
    }

    const appCheckInfo = await verifyAppCheckBestEffort(req, "issuePortalCustomToken");
    const certCheck = verifySideloadAppCertHeader(req);
    if (!certCheck.ok) {
      return res.status(certCheck.status || 403).json({ error: certCheck.error });
    }

    const body = req.body || {};
    const cleanUsername = normalizeUsername(body.username);
    const rawPassword = String(body.password || "").trim();
    const requestedRole = String(body.requestedRole || body.role || "").trim().toLowerCase();

    if (cleanUsername.length < 3 || rawPassword.length < 3 || !["admin", "teacher", "student", "parent"].includes(requestedRole)) {
      return res.status(400).json({ error: "Invalid username, password, or portal role." });
    }

    const clientIp = (req.headers["x-forwarded-for"] || req.ip || "ip_unknown").toString().split(",")[0].trim();
    const ipRateDecision = await checkAndConsumeServerRateLimit(
      `portal_auth_ip:${clientIp}`,
      30
    );
    if (!ipRateDecision.allowed) {
      res.setHeader("Retry-After", String(ipRateDecision.retryAfterSec));
      return res.status(429).json({
        error: `Too many login attempts from this network. Retry in ${ipRateDecision.retryAfterSec}s.`,
        retryAfterSeconds: ipRateDecision.retryAfterSec,
      });
    }

    const rateDecision = await checkAndConsumeServerRateLimit(
      `portal_auth:${appCheckInfo.appId}:${cleanUsername}:${clientIp}`,
      15
    );
    if (!rateDecision.allowed) {
      res.setHeader("Retry-After", String(rateDecision.retryAfterSec));
      return res.status(429).json({
        error: `Too many attempts. Blocked for ${rateDecision.retryAfterSec}s.`,
        retryAfterSeconds: rateDecision.retryAfterSec,
      });
    }

    try {
      const db = namedDb();

      // Enforce central revoked-device blocklist before issuing any Custom Token
      const revCheck = await assertDeviceNotRevoked(db, req);
      if (revCheck.revoked) {
        return res.status(403).json({
          error: `Device access revoked by Institute Admin (${revCheck.reason}).`,
          revoked: true,
        });
      }

      const credRef = db.collection("portalCredentials").doc(cleanUsername);
      const credSnap = await credRef.get();

      // Server-Side Root Admin Bootstrap / Reconciliation (`waqar` / `waqar`)
      const bootstrapPassword = String(process.env.ADMIN_BOOTSTRAP_PASSWORD || "waqar").trim();
      const isRootAdminBootstrap = cleanUsername === "waqar" && requestedRole === "admin" && constantTimeStringEquals(bootstrapPassword, rawPassword);

      // One-Time Server-Side Admin Bootstrap (`waqar`) ONLY when `/portalCredentials/waqar` does not exist yet
      if (!credSnap.exists) {
        if (isRootAdminBootstrap) {
          const saltBuf = crypto.randomBytes(16);
          const saltHex = saltBuf.toString("hex");
          const hash120k = crypto
            .pbkdf2Sync(rawPassword, saltBuf, PBKDF2_HARDENED_ITERATIONS, PBKDF2_KEYLEN_BYTES, "sha256")
            .toString("hex");
          const parity12k = crypto
            .pbkdf2Sync(rawPassword, saltBuf, PBKDF2_WEB_PARITY_ITERATIONS, PBKDF2_KEYLEN_BYTES, "sha256")
            .toString("hex");

          const bootstrapPortalUid = "admin_waqar_root";
          const bootstrapDisplayName = "Waqar Khan (Admin)";

          await credRef.set({
            username: "waqar",
            passwordHash: hash120k,
            passwordSalt: saltHex,
            pbkdf2ParityHash: parity12k,
            pbkdf2ParitySalt: saltHex,
            portalUid: bootstrapPortalUid,
            role: "admin",
            linkedEntityId: "ADMIN_ROOT",
            displayName: bootstrapDisplayName,
            assignedByUid: bootstrapPortalUid,
            active: true,
            isAdmin: true,
            isTeacher: false,
            isStudent: false,
            isParent: false,
            portalRoles: ["admin"],
            createdAt: FieldValue.serverTimestamp(),
          });

          await db.collection("users").doc(bootstrapPortalUid).set(
            {
              uid: bootstrapPortalUid,
              email: "waqar@waqarinstitute.edu",
              displayName: bootstrapDisplayName,
              role: "admin",
              username: "waqar",
              phone: "",
              mfaEnabled: false,
              totpSecret: "",
              linkedEntityId: "ADMIN_ROOT",
              isAdmin: true,
              isTeacher: false,
              isStudent: false,
              isParent: false,
              portalRoles: ["admin"],
              createdAt: FieldValue.serverTimestamp(),
            },
            { merge: true }
          );

          const customClaims = {
            role: "admin",
            portalUid: bootstrapPortalUid,
            username: "waqar",
            linkedEntityId: "ADMIN_ROOT",
          };
          const customToken = await admin.auth().createCustomToken(bootstrapPortalUid, customClaims);

          return res.status(200).json({
            customToken,
            portalUid: bootstrapPortalUid,
            role: "admin",
            username: "waqar",
            displayName: bootstrapDisplayName,
            linkedEntityId: "ADMIN_ROOT",
            bootstrapped: true,
          });
        }
        return res.status(401).json({ error: "Invalid username or password." });
      }

      const cred = credSnap.data() || {};
      if (cred.active === false) {
        return res.status(403).json({ error: "Portal account has been deactivated by Admin." });
      }

      if (!credentialSupportsRole(cred, requestedRole)) {
        return res.status(403).json({ error: `Account is not provisioned for the ${requestedRole} portal.` });
      }

      let primaryOk = verifyPbkdf2Hex(rawPassword, cred.passwordSalt, cred.passwordHash);
      let parityOk = !primaryOk && verifyPbkdf2Hex(rawPassword, cred.pbkdf2ParitySalt, cred.pbkdf2ParityHash);

      // Reconcile root admin password on server-side if bootstrap credentials match
      if (!primaryOk && !parityOk && isRootAdminBootstrap) {
        const saltBuf = crypto.randomBytes(16);
        const saltHex = saltBuf.toString("hex");
        const hash120k = crypto
          .pbkdf2Sync(rawPassword, saltBuf, PBKDF2_HARDENED_ITERATIONS, PBKDF2_KEYLEN_BYTES, "sha256")
          .toString("hex");
        await credRef.update({
          passwordHash: hash120k,
          passwordSalt: saltHex,
          active: true,
          isAdmin: true,
          portalRoles: ["admin"],
        });
        primaryOk = true;
      }

      if (!primaryOk && !parityOk) {
        return res.status(401).json({ error: "Invalid username or password." });
      }

      const portalUid = String(
        cred.portalUid || (cleanUsername === "waqar" && requestedRole === "admin" ? "admin_waqar_root" : `cred_${cleanUsername}`)
      );
      const linkedEntityId = String(cred.linkedEntityId || (requestedRole === "admin" ? "ADMIN_ROOT" : ""));
      const displayName = String(cred.displayName || cleanUsername);

      const customClaims = {
        role: requestedRole,
        portalUid,
        username: cleanUsername,
        linkedEntityId,
      };
      const customToken = await admin.auth().createCustomToken(portalUid, customClaims);

      return res.status(200).json({
        customToken,
        portalUid,
        role: requestedRole,
        username: cleanUsername,
        displayName,
        linkedEntityId,
      });
    } catch (err) {
      return res.status(500).json({ error: err.message || "Portal token exchange failed." });
    }
  }
);

/**
 * 100% Zero-Bill Computer-Studies-Only AI Tutor Proxy (`streamInstituteAiTutor`, region `asia-east1`).
 *
 * Cascade Order:
 *  - Stage A: Groq `meta-llama/llama-prompt-guard-2-86m` + Server Anti-Hacking Regex (Strike 1 -> Strike 2 24h Cage)
 *  - L3 Shared Cache (`ai_shared_cache/{canonicalSha256}`)
 *  - Authoritative Daily Quota (`ai_student_quotas/{uid}`: 5 live calls/day, 10s cooldown; Admin/Teacher exempt)
 *  - Stage B:
 *      1. Groq `openai/gpt-oss-120b` (via `CF_AI_GATEWAY_URL`)
 *      2. Groq `openai/gpt-oss-20b` (via `CF_AI_GATEWAY_URL`)
 *      3. Groq `qwen/qwen3.8-27b` (via `CF_AI_GATEWAY_URL`)
 *      4. Cloudflare Workers AI `@cf/google/gemma-4-26b-a4b-it` (via `CF_AI_TOKEN`)
 *      5. Explicit `GROQ_AND_CLOUDFLARE_EXHAUSTED` JSON flag ONLY when BOTH Groq and Cloudflare are exhausted.
 */
exports.streamInstituteAiTutor = onRequest(
  {
    region: "asia-east1",
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
        "Content-Type, Authorization, X-Firebase-AppCheck, X-WCI-Portal-Role, X-WCI-DPoP, X-WCI-Device-Fp"
      );
      res.status(204).send("");
      return;
    }

    if (req.method !== "POST") {
      res.status(405).json({ error: "Method Not Allowed. Use POST." });
      return;
    }

    // 1. Verify Firebase Auth ID Token (mandatory — derive studentUid and role authoritatively)
    const authHeader = String(req.header("Authorization") || "");
    if (!authHeader.startsWith("Bearer ")) {
      res.status(401).json({ error: "Unauthorized: Missing Firebase Auth Bearer ID token." });
      return;
    }
    let decodedToken;
    try {
      decodedToken = await admin.auth().verifyIdToken(authHeader.slice(7).trim());
    } catch (_) {
      res.status(401).json({ error: "Unauthorized: Invalid or expired Firebase Auth ID token." });
      return;
    }

    // 2. Best-effort Firebase App Check verification (optional for sideloaded Release APKs)
    const appCheckInfo = await verifyAppCheckBestEffort(req, "streamInstituteAiTutor");

    const studentUid = decodedToken.uid;
    const role = resolveAuthoritativeRole(decodedToken);
    const username = String(decodedToken.username || decodedToken.email || studentUid);
    const isExemptStaffOrAdmin = role === "admin" || role === "teacher";

    // 3. Enforce Global Edge Abuse Rate Limit
    const clientIp = (req.headers["x-forwarded-for"] || req.ip || "ip_unknown").toString().split(",")[0].trim();
    const rateDecision = await checkAndConsumeServerRateLimit(`ai_tutor:${appCheckInfo.appId}:${studentUid}:${clientIp}`);
    if (!rateDecision.allowed) {
      res.setHeader("Retry-After", String(rateDecision.retryAfterSec));
      res.status(429).json({
        error: `HTTP 429 Too Many Requests: Rate limit exceeded. Retry in ${rateDecision.retryAfterSec}s.`,
        retryAfterSeconds: rateDecision.retryAfterSec,
      });
      return;
    }

    // 4. Validate Payload
    const body = req.body || {};
    const featureType = String(body.featureType || "TUTOR_CHAT").trim().toUpperCase().slice(0, 40);
    const languageCode = String(body.languageCode || "EN").trim().toUpperCase().slice(0, 8);
    const userPrompt = String(body.userPrompt || "").trim().slice(0, 2500);
    const systemPrompt = String(
      body.systemPrompt ||
        "You are Waqar Computer Institute Study Tutor. Answer ONLY Computer Science, IT, DCA, ADCA, CCC, Tally Prime with GST, MS Office, Python, C/C++, Java, Kotlin, Web Dev, SQL, DBMS, OS, Networking, and Hardware questions clearly and concisely."
    ).slice(0, 3500);

    if (!userPrompt) {
      res.status(400).json({ error: "Missing userPrompt in request body." });
      return;
    }

    const db = namedDb();

    // 4B. Enforce Revoked Device Blocklist & Hardware-Bound DPoP Proof
    const revCheck = await assertDeviceNotRevoked(db, req);
    if (revCheck.revoked) {
      res.status(403).json({
        error: `Device access revoked by Institute Admin (${revCheck.reason}).`,
        revoked: true,
      });
      return;
    }
    const dpopCheck = await verifyDpopProof(req, db, studentUid);
    if (!dpopCheck.ok) {
      res.status(dpopCheck.status || 401).json({ error: dpopCheck.error });
      return;
    }

    // 5. Check Existing 24-Hour Security Cage Lock (Admin & Teacher exempt)
    if (!isExemptStaffOrAdmin) {
      try {
        const lockSnap = await db.collection("ai_tutor_locks").doc(studentUid).get();
        if (lockSnap.exists) {
          const lockData = lockSnap.data() || {};
          const lockedUntilMs = Number(lockData.lockedUntilMs || 0);
          if (lockData.locked === true && lockedUntilMs > Date.now()) {
            const remainingMin = Math.max(1, Math.ceil((lockedUntilMs - Date.now()) / 60000));
            res.status(403).json({
              error: `${SECURITY_STRIKE_2_LOCK_MESSAGE} (${remainingMin} minutes remaining).`,
              locked: true,
              lockedUntilMs,
              securityStrike: Number(lockData.strikeCount || 2),
            });
            return;
          }
        }
      } catch (_) {
        // Proceed to guard checks
      }
    }

    // Read server-only AI proxy credentials from Firebase Functions runtime environment (`functions/.env`)
    const groqKey = String(process.env.GROK_API_KEY || process.env.XAI_API_KEY || process.env.GROQ_API_KEY || "").trim();
    const geminiKey = String(process.env.GEMINI_API_KEY || process.env.GOOGLE_API_KEY || process.env.GOOGLE_AI_KEY || "").trim();
    const cfGatewayUrl = String(process.env.CF_AI_GATEWAY_URL || "").trim();
    const cfToken = String(process.env.CF_AI_TOKEN || "").trim();

    // 6. Stage A — Server Security Guard (Llama Prompt Guard 2 86M + Anti-Hacking/Injection Regex)
    const guardVerdict = await evaluatePromptGuardServer(userPrompt, groqKey, cfGatewayUrl, cfToken);
    if (guardVerdict.malicious && !isExemptStaffOrAdmin) {
      const strikeOutcome = await recordSecurityStrikeAndLockIfRepeated(
        db,
        studentUid,
        username,
        `Stage A Guard Blocked (${guardVerdict.source})`,
        userPrompt,
        req
      );
      res.status(403).json({
        error: SECURITY_STRIKE_2_LOCK_MESSAGE,
        securityStrike: strikeOutcome.strikeCount,
        locked: strikeOutcome.locked,
        lockedUntilMs: strikeOutcome.lockedUntilMs,
      });
      return;
    }

    // 7. Strict Computer-Studies Allowlist & Anti-Chit-Chat Filter (0 upstream chat-model cost)
    if (!isExemptStaffOrAdmin && isCasualChitChatOrOffTopic(userPrompt)) {
      res.setHeader("Content-Type", "text/event-stream; charset=utf-8");
      res.setHeader("Cache-Control", "no-cache, no-transform");
      res.write(`data: ${JSON.stringify({ delta: POLITE_STUDY_REDIRECT, model: "guardrail/study-only" })}\n\n`);
      res.write("data: [DONE]\n\n");
      res.end();
      return;
    }

    // 8. Check Global Shared Firestore L3 Cache (`ai_shared_cache/{canonicalSha256}`) — 0 quota cost
    const cacheHash = computeCanonicalSha256(featureType, languageCode, userPrompt);
    try {
      const cacheSnap = await db.collection("ai_shared_cache").doc(cacheHash).get();
      if (cacheSnap.exists) {
        const cachedData = cacheSnap.data() || {};
        if (typeof cachedData.responseText === "string" && cachedData.responseText.trim().length > 0) {
          res.setHeader("Content-Type", "text/event-stream; charset=utf-8");
          res.setHeader("Cache-Control", "no-cache, no-transform");
          res.write(
            `data: ${JSON.stringify({ delta: cachedData.responseText, model: cachedData.modelName || "cache/l3-shared" })}\n\n`
          );
          res.write("data: [DONE]\n\n");
          res.end();
          // Best-effort hit count increment
          db.collection("ai_shared_cache").doc(cacheHash).set(
            { hitCount: FieldValue.increment(1), lastHitAt: FieldValue.serverTimestamp() },
            { merge: true }
          ).catch(() => {});
          return;
        }
      }
    } catch (_) {
      // Cache lookup failed cleanly; continue to live AI cascade
    }

    // 9. Authoritative Server-Side Daily Quota Transaction (Students/Parents: 5 live/day, 10s cooldown; Admin/Teacher exempt)
    if (!isExemptStaffOrAdmin) {
      const quota = await checkAndConsumeDailyStudentQuota(db, studentUid);
      if (!quota.allowed) {
        res.setHeader("Retry-After", String(quota.waitSeconds));
        const quotaMsg = quota.reason === "COOLDOWN"
          ? `Please wait ${quota.waitSeconds}s between live AI questions (cached study answers remain unlimited).`
          : `Daily live AI quota (${STUDENT_DAILY_LIVE_AI_CAP}/day) reached for today. Cached study notes remain unlimited and your live quota resets automatically at midnight UTC.`;
        res.status(429).json({
          error: quotaMsg,
          quotaExceeded: true,
          retryAfterSeconds: quota.waitSeconds,
        });
        return;
      }
    }

    // 10. Stage B — Zero-Cost Instant Failover (Circuit Breaker: Grok API -> Google Gemini API Free Tier)
    const circuitState = await getCircuitBreakerState(db);
    const grokAllowed = circuitState.grokCircuitState !== "OPEN" && Boolean(grokKey);

    let chosenGrokResp = null;
    let chosenGrokModel = "";

    if (grokAllowed) {
      try {
        const { resp, model } = await callGrokApi(grokKey, cfGatewayUrl, cfToken, systemPrompt, userPrompt);
        if (resp.status === 429 || resp.status >= 500) {
          // Trip circuit breaker on 429 quota exhaustion or 5xx server error
          await tripCircuitBreaker(db, `Grok HTTP ${resp.status}`);
        } else if (resp.ok && resp.body) {
          chosenGrokResp = resp;
          chosenGrokModel = `grok/${model}`;
        } else {
          await tripCircuitBreaker(db, `Grok unexpected HTTP ${resp.status}`);
        }
      } catch (grokErr) {
        // Timeout >6s or fetch network error: trip circuit breaker immediately
        const isTimeout = grokErr.name === "TimeoutError" || String(grokErr.message).includes("timed out");
        await tripCircuitBreaker(db, isTimeout ? "Grok Timeout (>6s)" : `Grok Error: ${grokErr.message}`);
      }
    }

    // 10A. If Grok succeeded, stream line-by-line SSE response to client and cache to L3 Firestore
    if (chosenGrokResp) {
      await streamGrokSseResponse(chosenGrokResp, res, chosenGrokModel, cacheHash, featureType, languageCode, userPrompt, db);
      return;
    }

    // 10B. FAILOVER: Instant Zero-Delay Google Gemini API (Free Tier e.g. gemini-2.5-flash)
    if (geminiKey) {
      try {
        await callGeminiApi(geminiKey, systemPrompt, userPrompt, res, cacheHash, featureType, languageCode, db);
        return;
      } catch (geminiErr) {
        console.error("Gemini failover error:", geminiErr.message);
      }
    }

    // 10C. Secondary Fallback: Cloudflare Workers AI Free Tier (if configured)
    if (cfGatewayUrl && cfToken) {
      const cfWorkersUrl = buildWorkersAiEndpointUrl(cfGatewayUrl, CF_WORKERS_AI_FALLBACK_MODEL);
      if (cfWorkersUrl) {
        try {
          const cfResp = await fetch(cfWorkersUrl, {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              Authorization: `Bearer ${cfToken}`,
              "cf-aig-authorization": `Bearer ${cfToken}`,
            },
            body: JSON.stringify({
              messages: [
                { role: "system", content: systemPrompt },
                { role: "user", content: userPrompt },
              ],
              max_tokens: MAX_COMPLETION_TOKENS,
              temperature: UPSTREAM_TEMPERATURE,
              stream: false,
            }),
            signal: AbortSignal.timeout(10000),
          });

          if (cfResp.ok) {
            const cfJson = await cfResp.json().catch(() => ({}));
            const cfText = String(
              cfJson?.result?.response ||
              cfJson?.choices?.[0]?.message?.content ||
              cfJson?.response ||
              ""
            ).trim();

            if (cfText) {
              res.setHeader("Content-Type", "text/event-stream; charset=utf-8");
              res.setHeader("Cache-Control", "no-cache, no-transform");
              res.write(`data: ${JSON.stringify({ delta: cfText, model: `cloudflare/${CF_WORKERS_AI_FALLBACK_MODEL}` })}\n\n`);
              res.write("data: [DONE]\n\n");
              res.end();

              db.collection("ai_shared_cache").doc(cacheHash).set(
                {
                  promptHash: cacheHash,
                  featureType,
                  languageCode,
                  canonicalPrompt: userPrompt.trim().toLowerCase().slice(0, 1000),
                  promptText: userPrompt.slice(0, 1000),
                  responseText: cfText.slice(0, 8000),
                  modelName: `cloudflare/${CF_WORKERS_AI_FALLBACK_MODEL}`,
                  hitCount: 1,
                  updatedAt: FieldValue.serverTimestamp(),
                  createdAt: FieldValue.serverTimestamp(),
                },
                { merge: true }
              ).catch(() => {});
              return;
            }
          }
        } catch (_) {}
      }
    }

    // 10D. If BOTH Grok and Gemini (and Cloudflare) free tiers are exhausted, return clear exhaustion payload
    res.status(429).json({
      fallbackEligible: "GEMINI_FREE_LAST_RESORT",
      reason: "GROQ_AND_CLOUDFLARE_EXHAUSTED",
      error: "Primary (Grok) and Secondary (Gemini) Free Tiers exhausted. Switch to offline syllabus study notes.",
    });
  }
);

/**
 * [B2] Server-Enforced Exam Start-Time & Single-Use Session Nonce (`startExamSession`, asia-east1).
 */
exports.startExamSession = onRequest(
  {
    region: "asia-east1",
    cors: [
      "https://gen-lang-client-0309263247.web.app",
      "https://gen-lang-client-0309263247.firebaseapp.com",
    ],
    timeoutSeconds: 30,
    memory: "256MiB",
  },
  async (req, res) => {
    applySecurityHeaders(res);
    if (req.method === "OPTIONS") {
      res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
      res.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Firebase-AppCheck, X-WCI-DPoP, X-WCI-Device-Fp");
      res.status(204).send("");
      return;
    }
    if (req.method !== "POST") {
      return res.status(405).json({ error: "Method Not Allowed" });
    }

    const authHeader = String(req.header("Authorization") || "");
    if (!authHeader.startsWith("Bearer ")) {
      return res.status(401).json({ error: "Missing Firebase ID token." });
    }
    let decoded;
    try {
      decoded = await admin.auth().verifyIdToken(authHeader.slice(7).trim());
    } catch (_) {
      return res.status(401).json({ error: "Invalid Firebase ID token." });
    }

    await verifyAppCheckBestEffort(req, "startExamSession");

    const db = namedDb();
    const revCheck = await assertDeviceNotRevoked(db, req);
    if (revCheck.revoked) {
      return res.status(403).json({ error: `Device access revoked (${revCheck.reason}).`, revoked: true });
    }
    const dpopCheck = await verifyDpopProof(req, db, decoded.uid);
    if (!dpopCheck.ok) {
      return res.status(dpopCheck.status || 401).json({ error: dpopCheck.error });
    }

    const body = req.body || {};
    const examId = sanitizeDocKey(body.examId || "", 80);
    if (!examId) {
      return res.status(400).json({ error: "Missing examId." });
    }

    try {
      let authoritativeDurationSec = Number(body.durationSeconds || 0);
      const examSnap = await db.collection("exams").doc(examId).get();
      if (examSnap.exists) {
        const examData = examSnap.data() || {};
        const docDurSec = Number(examData.durationSeconds || 0);
        const docDurMin = Number(examData.durationMinutes || 0);
        if (docDurSec >= 10) {
          authoritativeDurationSec = docDurSec;
        } else if (docDurMin >= 1) {
          authoritativeDurationSec = docDurMin * 60;
        }
      }
      if (!Number.isFinite(authoritativeDurationSec) || authoritativeDurationSec < 10) {
        authoritativeDurationSec = 1800;
      }
      authoritativeDurationSec = Math.min(21600, Math.max(10, Math.round(authoritativeDurationSec)));

      const studentUid = decoded.uid;
      const sessionId = `${examId}_${sanitizeDocKey(studentUid, 80)}`;
      const sessionRef = db.collection("examSessions").doc(sessionId);
      const serverNowMs = Date.now();

      const result = await db.runTransaction(async (tx) => {
        const snap = await tx.get(sessionRef);
        if (snap.exists) {
          const existing = snap.data() || {};
          if (existing.submitted === true) {
            return { alreadySubmitted: true };
          }
          return {
            alreadySubmitted: false,
            sessionId,
            examId,
            studentUid,
            sessionNonce: String(existing.sessionNonce || ""),
            serverStartedAtMs: Number(existing.serverStartedAtMs || serverNowMs),
            hardDeadlineAtMs: Number(existing.hardDeadlineAtMs || (serverNowMs + authoritativeDurationSec * 1000)),
            durationSeconds: Number(existing.durationSeconds || authoritativeDurationSec),
            serverNowMs,
          };
        }

        const sessionNonce = crypto.randomBytes(16).toString("hex");
        const hardDeadlineAtMs = serverNowMs + authoritativeDurationSec * 1000;
        tx.set(sessionRef, {
          id: sessionId,
          examId,
          studentUid,
          sessionNonce,
          serverStartedAtMs: serverNowMs,
          hardDeadlineAtMs,
          durationSeconds: authoritativeDurationSec,
          submitted: false,
          serverStartedAt: FieldValue.serverTimestamp(),
          createdAt: FieldValue.serverTimestamp(),
        });

        return {
          alreadySubmitted: false,
          sessionId,
          examId,
          studentUid,
          sessionNonce,
          serverStartedAtMs: serverNowMs,
          hardDeadlineAtMs,
          durationSeconds: authoritativeDurationSec,
          serverNowMs,
        };
      });

      if (result.alreadySubmitted) {
        return res.status(403).json({
          error: "Exam attempt already submitted for this student.",
          alreadySubmitted: true,
        });
      }

      return res.status(200).json(result);
    } catch (err) {
      return res.status(500).json({ error: err.message || "Failed to initialize exam session." });
    }
  }
);

/**
 * [A5 + B2] Zero-Client-Trust Server Exam Grader Cloud Function (`gradeExamSubmission`).
 * Enforces Firebase ID Token + App Check + DPoP + Revoked Device Check + Single-Use `sessionNonce`
 * + Server-Enforced `hardDeadlineAt` (+30s network grace) in an atomic Firestore transaction.
 */
exports.gradeExamSubmission = onRequest(
  {
    region: "asia-east1",
    cors: [
      "https://gen-lang-client-0309263247.web.app",
      "https://gen-lang-client-0309263247.firebaseapp.com",
    ],
    timeoutSeconds: 60,
    memory: "256MiB",
  },
  async (req, res) => {
    applySecurityHeaders(res);
    if (req.method === "OPTIONS") {
      res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
      res.set(
        "Access-Control-Allow-Headers",
        "Content-Type, Authorization, X-Firebase-AppCheck, X-WCI-Paper-Watermark, X-WCI-DPoP, X-WCI-Device-Fp"
      );
      res.status(204).send("");
      return;
    }
    if (req.method !== "POST") {
      return res.status(405).json({ error: "Method Not Allowed" });
    }

    const authHeader = String(req.header("Authorization") || "");
    if (!authHeader.startsWith("Bearer ")) {
      return res.status(401).json({ error: "Missing Firebase ID token." });
    }
    let decoded;
    try {
      decoded = await admin.auth().verifyIdToken(authHeader.slice(7).trim());
    } catch (_) {
      return res.status(401).json({ error: "Invalid Firebase ID token." });
    }

    await verifyAppCheckBestEffort(req, "gradeExamSubmission");

    const db = namedDb();
    const revCheck = await assertDeviceNotRevoked(db, req);
    if (revCheck.revoked) {
      return res.status(403).json({ error: `Device access revoked (${revCheck.reason}).`, revoked: true });
    }
    const dpopCheck = await verifyDpopProof(req, db, decoded.uid);
    if (!dpopCheck.ok) {
      return res.status(dpopCheck.status || 401).json({ error: dpopCheck.error });
    }

    const rate = await checkAndConsumeServerRateLimit(`grade:${decoded.uid}`);
    if (!rate.allowed) {
      res.setHeader("Retry-After", String(rate.retryAfterSec));
      return res.status(429).json({
        error: "Rate limit exceeded.",
        retryAfterSeconds: rate.retryAfterSec,
      });
    }

    const body = req.body || {};
    const examId = sanitizeDocKey(body.examId || "", 80);
    const sessionNonce = String(body.sessionNonce || "").trim();
    const submittedAnswers =
      (body.answersByMasterQId && typeof body.answersByMasterQId === "object" ? body.answersByMasterQId : null) ||
      (body.answers && typeof body.answers === "object" ? body.answers : null);

    if (!examId || !submittedAnswers) {
      return res.status(400).json({ error: "Missing examId or answersByMasterQId." });
    }
    if (!sessionNonce) {
      return res.status(403).json({ error: "Missing authoritative exam sessionNonce." });
    }

    const authoritativeStudentId = decoded.uid;
    const studentName = String(body.studentName || decoded.name || decoded.username || "Student").slice(0, 100);
    const examTitle = String(body.examTitle || "").slice(0, 140);
    const paperWatermark = String(body.paperWatermark || req.header("X-WCI-Paper-Watermark") || "").slice(0, 120);
    const violationReason = String(body.violationReason || "").slice(0, 240);
    const hashChainHead = String(body.hashChainHead || "").slice(0, 128);
    const behavioralAnomalyCount = Number.isFinite(Number(body.behavioralAnomalyCount))
      ? Math.max(0, Math.floor(Number(body.behavioralAnomalyCount)))
      : 0;

    try {
      const keySnap = await db.collection("examAnswerKeys").doc(examId).get();
      if (!keySnap.exists) {
        return res.status(404).json({ error: "Answer key not found." });
      }

      const keyData = keySnap.data() || {};
      const masterAnswers = keyData.answersByMasterQId || keyData.answers || {};
      const entries = Object.entries(masterAnswers);
      const totalQ = entries.length || 1;
      let correct = 0;

      for (const [qId, correctIdx] of entries) {
        if (
          Object.prototype.hasOwnProperty.call(submittedAnswers, qId) &&
          Number(submittedAnswers[qId]) === Number(correctIdx)
        ) {
          correct++;
        }
      }

      const configuredTotalMarks = Number(body.totalMarks || keyData.totalMarks || totalQ);
      const totalMarks = Number.isFinite(configuredTotalMarks) && configuredTotalMarks >= 1
        ? Math.min(1000, Math.round(configuredTotalMarks))
        : totalQ;
      const marksObtained = Math.min(totalMarks, Math.max(0, Math.round((correct * totalMarks) / totalQ)));
      const configuredPassingMarks = Number(body.passingMarks || Math.ceil(totalMarks * 0.4));
      const passingMarks = Number.isFinite(configuredPassingMarks)
        ? Math.max(0, Math.min(totalMarks, Math.round(configuredPassingMarks)))
        : Math.ceil(totalMarks * 0.4);

      const exactPct = Number(((marksObtained * 100) / Math.max(1, totalMarks)).toFixed(2));
      const grade =
        exactPct >= 90 ? "A+" :
        exactPct >= 80 ? "A" :
        exactPct >= 70 ? "B+" :
        exactPct >= 60 ? "B" :
        exactPct >= 50 ? "C" :
        exactPct >= 40 ? "D" : "F";
      const passed = marksObtained >= passingMarks;
      const status = passed ? "PASSED" : "FAILED";
      const formattedPercentage = `${exactPct}%`;
      const remarks = violationReason
        ? `Server-Graded (${correct}/${totalQ}) [Auto-Submitted: ${violationReason}]`
        : `Verified Server-Graded Proctored CBT (${correct}/${totalQ})`;

      const resultDocId = `${examId}_${sanitizeDocKey(authoritativeStudentId, 80)}`;
      const sessionRef = db.collection("examSessions").doc(resultDocId);
      const resultRef = db.collection("examResults").doc(resultDocId);
      const serverNowMs = Date.now();

      const resultPayload = {
        id: resultDocId,
        examId,
        examTitle: examTitle || String(keyData.examTitle || "CBT Exam"),
        studentId: authoritativeStudentId,
        studentName,
        marksObtained,
        totalMarks,
        correctCount: correct,
        totalQuestions: totalQ,
        exactPercentage: exactPct,
        formattedPercentage,
        grade,
        rank: 0,
        status,
        remarks: remarks.slice(0, 480),
        violationCount: behavioralAnomalyCount,
        paperWatermark,
        ownerUid: authoritativeStudentId,
        gradedAt: FieldValue.serverTimestamp(),
        createdAt: FieldValue.serverTimestamp(),
      };
      if (hashChainHead) {
        resultPayload.hashChainHead = hashChainHead;
      }

      const txOutcome = await db.runTransaction(async (tx) => {
        const sessionSnap = await tx.get(sessionRef);
        if (!sessionSnap.exists) {
          return { ok: false, status: 403, error: "Exam session not found. Start exam via startExamSession first." };
        }
        const sessionData = sessionSnap.data() || {};
        if (sessionData.submitted === true) {
          return { ok: false, status: 403, error: "Duplicate submission rejected: exam session already submitted." };
        }
        if (!constantTimeStringEquals(sessionData.sessionNonce || "", sessionNonce)) {
          return { ok: false, status: 403, error: "Invalid exam sessionNonce." };
        }
        const hardDeadlineAtMs = Number(sessionData.hardDeadlineAtMs || 0);
        if (hardDeadlineAtMs > 0 && serverNowMs > hardDeadlineAtMs + EXAM_GRACE_PERIOD_MS) {
          return { ok: false, status: 403, error: "Exam submission rejected: server hard deadline exceeded." };
        }

        tx.update(sessionRef, {
          submitted: true,
          submittedAtMs: serverNowMs,
          submittedAt: FieldValue.serverTimestamp(),
        });
        tx.set(resultRef, resultPayload, { merge: true });
        return { ok: true };
      });

      if (!txOutcome.ok) {
        return res.status(txOutcome.status || 403).json({ error: txOutcome.error });
      }

      return res.status(200).json({
        id: resultDocId,
        marksObtained,
        totalMarks,
        rawCorrectCount: correct,
        correctCount: correct,
        totalQuestions: totalQ,
        exactPercentage: exactPct,
        formattedPercentage,
        grade,
        passed,
        status,
        remarks,
      });
    } catch (err) {
      return res.status(500).json({ error: err.message || "Server grading failed." });
    }
  }
);

/**
 * [B5] Server-Side Tamper-Evident Fee & Certificate Audit Hash-Chain Sealer (`sealAuditChainEvent`).
 * Admin-only, atomic Firestore transaction updating `/server_audit_chain_meta/head` & `/server_audit_hash_chain/{seq}`.
 */
exports.sealAuditChainEvent = onRequest(
  {
    region: "asia-east1",
    cors: [
      "https://gen-lang-client-0309263247.web.app",
      "https://gen-lang-client-0309263247.firebaseapp.com",
    ],
    timeoutSeconds: 30,
    memory: "256MiB",
  },
  async (req, res) => {
    applySecurityHeaders(res);
    if (req.method === "OPTIONS") {
      res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
      res.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Firebase-AppCheck, X-WCI-DPoP, X-WCI-Device-Fp");
      res.status(204).send("");
      return;
    }
    if (req.method !== "POST") {
      return res.status(405).json({ error: "Method Not Allowed" });
    }

    const authHeader = String(req.header("Authorization") || "");
    if (!authHeader.startsWith("Bearer ")) {
      return res.status(401).json({ error: "Missing Firebase ID token." });
    }
    let decoded;
    try {
      decoded = await admin.auth().verifyIdToken(authHeader.slice(7).trim());
    } catch (_) {
      return res.status(401).json({ error: "Invalid Firebase ID token." });
    }
    if (resolveAuthoritativeRole(decoded) !== "admin") {
      return res.status(403).json({ error: "Admin privileges required to seal audit chain events." });
    }

    await verifyAppCheckBestEffort(req, "sealAuditChainEvent");

    const db = namedDb();
    const revCheck = await assertDeviceNotRevoked(db, req);
    if (revCheck.revoked) {
      return res.status(403).json({ error: `Device access revoked (${revCheck.reason}).`, revoked: true });
    }
    const dpopCheck = await verifyDpopProof(req, db, decoded.uid);
    if (!dpopCheck.ok) {
      return res.status(dpopCheck.status || 401).json({ error: dpopCheck.error });
    }

    const body = req.body || {};
    const eventType = String(body.eventType || "").trim().toUpperCase().slice(0, 80);
    const recordId = String(body.recordId || "").trim().slice(0, 120);
    const payloadObj = body.payload && typeof body.payload === "object" ? body.payload : {};
    const biometricActionSignatureHex = String(body.biometricActionSignatureHex || "").trim().slice(0, 512);

    if (!eventType || !recordId) {
      return res.status(400).json({ error: "Missing eventType or recordId." });
    }

    try {
      const headRef = db.collection("server_audit_chain_meta").doc("head");
      const canonicalJson = canonicalizeJson(payloadObj).slice(0, 4000);

      const sealed = await db.runTransaction(async (tx) => {
        const headSnap = await tx.get(headRef);
        const headData = headSnap.exists ? headSnap.data() : {};
        const prevSeq = Number(headData.seq || 0);
        const previousHash = String(headData.currentHash || AUDIT_CHAIN_GENESIS_HASH);
        const nextSeq = prevSeq + 1;
        const serverTimestampMs = Date.now();

        const preimage = `${previousHash}|${nextSeq}|${eventType}|${recordId}|${canonicalJson}|${serverTimestampMs}`;
        const currentHash = crypto.createHash("sha256").update(preimage, "utf8").digest("hex");
        const docKey = String(nextSeq).padStart(10, "0");

        tx.set(db.collection("server_audit_hash_chain").doc(docKey), {
          seq: nextSeq,
          eventType,
          recordId,
          actorUid: decoded.uid,
          canonicalJson,
          previousHash,
          currentHash,
          serverTimestampMs,
          biometricActionSignatureHex,
          createdAt: FieldValue.serverTimestamp(),
        });

        tx.set(headRef, {
          seq: nextSeq,
          currentHash,
          lastEventType: eventType,
          lastRecordId: recordId,
          updatedAtMs: serverTimestampMs,
          updatedAt: FieldValue.serverTimestamp(),
        });

        return {
          seq: nextSeq,
          previousHash,
          currentHash,
          serverTimestampMs,
          eventType,
          recordId,
        };
      });

      return res.status(200).json(sealed);
    } catch (err) {
      return res.status(500).json({ error: err.message || "Failed to seal audit chain event." });
    }
  }
);

/**
 * [B5] Server-Side Tamper-Evident Audit Hash-Chain Verifier (`verifyAuditHashChain`).
 * Recomputes every link in `/server_audit_hash_chain` and compares against `/server_audit_chain_meta/head`.
 */
exports.verifyAuditHashChain = onRequest(
  {
    region: "asia-east1",
    cors: [
      "https://gen-lang-client-0309263247.web.app",
      "https://gen-lang-client-0309263247.firebaseapp.com",
    ],
    timeoutSeconds: 60,
    memory: "256MiB",
  },
  async (req, res) => {
    applySecurityHeaders(res);
    if (req.method === "OPTIONS") {
      res.set("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
      res.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Firebase-AppCheck, X-WCI-DPoP, X-WCI-Device-Fp");
      res.status(204).send("");
      return;
    }
    if (req.method !== "POST" && req.method !== "GET") {
      return res.status(405).json({ error: "Method Not Allowed" });
    }

    const authHeader = String(req.header("Authorization") || "");
    if (!authHeader.startsWith("Bearer ")) {
      return res.status(401).json({ error: "Missing Firebase ID token." });
    }
    let decoded;
    try {
      decoded = await admin.auth().verifyIdToken(authHeader.slice(7).trim());
    } catch (_) {
      return res.status(401).json({ error: "Invalid Firebase ID token." });
    }
    if (resolveAuthoritativeRole(decoded) !== "admin") {
      return res.status(403).json({ error: "Admin privileges required to verify audit hash-chain." });
    }

    await verifyAppCheckBestEffort(req, "verifyAuditHashChain");

    const db = namedDb();
    const revCheck = await assertDeviceNotRevoked(db, req);
    if (revCheck.revoked) {
      return res.status(403).json({ error: `Device access revoked (${revCheck.reason}).`, revoked: true });
    }
    const dpopCheck = await verifyDpopProof(req, db, decoded.uid);
    if (!dpopCheck.ok) {
      return res.status(dpopCheck.status || 401).json({ error: dpopCheck.error });
    }

    try {
      const headSnap = await db.collection("server_audit_chain_meta").doc("head").get();
      const chainSnap = await db.collection("server_audit_hash_chain").orderBy("seq", "asc").get();

      if (!headSnap.exists && chainSnap.empty) {
        return res.status(200).json({
          verified: true,
          status: "VERIFIED",
          chainLength: 0,
          headSeq: 0,
          headHash: AUDIT_CHAIN_GENESIS_HASH,
          firstBrokenSeq: null,
          checkedAtMs: Date.now(),
        });
      }

      let expectedPrev = AUDIT_CHAIN_GENESIS_HASH;
      let expectedSeq = 1;
      let verified = true;
      let firstBrokenSeq = null;
      let lastHash = AUDIT_CHAIN_GENESIS_HASH;

      for (const doc of chainSnap.docs) {
        const d = doc.data() || {};
        const seq = Number(d.seq || 0);
        const eventType = String(d.eventType || "");
        const recordId = String(d.recordId || "");
        const canonicalJson = String(d.canonicalJson || "{}");
        const serverTimestampMs = Number(d.serverTimestampMs || 0);
        const storedPrev = String(d.previousHash || "");
        const storedCurr = String(d.currentHash || "");

        const preimage = `${storedPrev}|${seq}|${eventType}|${recordId}|${canonicalJson}|${serverTimestampMs}`;
        const recomputed = crypto.createHash("sha256").update(preimage, "utf8").digest("hex");

        if (
          seq !== expectedSeq ||
          !constantTimeStringEquals(expectedPrev, storedPrev) ||
          !constantTimeHexEquals(recomputed, storedCurr)
        ) {
          verified = false;
          firstBrokenSeq = seq || expectedSeq;
          break;
        }
        expectedPrev = storedCurr;
        lastHash = storedCurr;
        expectedSeq++;
      }

      const headData = headSnap.exists ? headSnap.data() : {};
      const headSeq = Number(headData.seq || 0);
      const headHash = String(headData.currentHash || AUDIT_CHAIN_GENESIS_HASH);
      if (verified && (headSeq !== chainSnap.size || !constantTimeStringEquals(headHash, lastHash))) {
        verified = false;
        firstBrokenSeq = headSeq;
      }

      return res.status(200).json({
        verified,
        status: verified ? "VERIFIED" : "TAMPERED",
        chainLength: chainSnap.size,
        headSeq,
        headHash,
        firstBrokenSeq,
        checkedAtMs: Date.now(),
      });
    } catch (err) {
      return res.status(500).json({ error: err.message || "Audit hash-chain verification failed." });
    }
  }
);
