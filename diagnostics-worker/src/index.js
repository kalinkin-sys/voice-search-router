import { EmailMessage } from "cloudflare:email";

const PATH = "/v1/diagnostics";
const MAX_REPORT_BYTES = 32 * 1024;
const INSTALLATION_ID = /^[a-zA-Z0-9][a-zA-Z0-9._-]{15,127}$/;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname !== PATH) return json(404, { ok: false, error: "not_found" });
    if (request.method !== "POST") {
      return json(405, { ok: false, error: "method_not_allowed" }, { Allow: "POST" });
    }

    const contentType = request.headers.get("content-type") || "";
    if (!contentType.toLowerCase().startsWith("text/plain")) {
      return json(415, { ok: false, error: "text_plain_required" });
    }
    if (request.headers.get("x-voice-router-version") !== "1") {
      return json(400, { ok: false, error: "unsupported_client" });
    }

    const installationId = request.headers.get("x-voice-router-install") || "";
    if (!INSTALLATION_ID.test(installationId)) {
      return json(400, { ok: false, error: "invalid_installation" });
    }

    const declaredLength = Number(request.headers.get("content-length") || 0);
    if (declaredLength > MAX_REPORT_BYTES) {
      return json(413, { ok: false, error: "report_too_large" });
    }

    const [perInstall, global] = await Promise.all([
      env.PER_INSTALL_LIMITER.limit({ key: installationId }),
      env.GLOBAL_LIMITER.limit({ key: "voice-router-diagnostics" }),
    ]);
    if (!perInstall.success || !global.success) {
      return json(429, { ok: false, error: "rate_limited" }, { "Retry-After": "60" });
    }

    let report;
    try {
      report = await readLimitedText(request, MAX_REPORT_BYTES);
    } catch (error) {
      return json(413, { ok: false, error: "report_too_large" });
    }
    if (!isDiagnosticReport(report)) {
      return json(422, { ok: false, error: "invalid_report" });
    }

    const reportId = crypto.randomUUID();
    const received = new Date();
    const body = [
      "Voice Search Router diagnostic report",
      `Report ID: ${reportId}`,
      `Received: ${received.toISOString()}`,
      `Installation: ${installationId}`,
      "",
      report,
    ].join("\r\n");
    const raw = makeEmail(env.FROM_ADDRESS, env.TO_ADDRESS,
      `Voice Search Router diagnostics ${reportId.slice(0, 8)}`, body, received, reportId);

    try {
      await env.DIAGNOSTICS_EMAIL.send(
        new EmailMessage(env.FROM_ADDRESS, env.TO_ADDRESS, raw),
      );
    } catch (error) {
      console.error("email_send_failed", reportId, error);
      return json(502, { ok: false, error: "delivery_failed", reportId });
    }

    console.log("diagnostic_delivered", reportId);
    return json(202, { ok: true, reportId });
  },
};

async function readLimitedText(request, limit) {
  if (!request.body) return "";
  const reader = request.body.getReader();
  const chunks = [];
  let size = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > limit) {
      await reader.cancel("report_too_large");
      throw new Error("report_too_large");
    }
    chunks.push(value);
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return new TextDecoder("utf-8", { fatal: true }).decode(bytes);
}

function isDiagnosticReport(report) {
  return report.length >= 80
    && report.startsWith("Voice Search Router diagnostics\n")
    && report.includes("\nRouter: ")
    && report.includes("\nDevice: ")
    && report.includes("\nRecent Router events");
}

function makeEmail(from, to, subject, body, date, reportId) {
  const safeFrom = singleLine(from);
  const safeTo = singleLine(to);
  const safeSubject = singleLine(subject);
  return [
    `From: Voice Search Router <${safeFrom}>`,
    `To: ${safeTo}`,
    `Subject: ${safeSubject}`,
    `Date: ${date.toUTCString()}`,
    `Message-ID: <${reportId}@voice-router-diagnostics>`,
    "MIME-Version: 1.0",
    "Content-Type: text/plain; charset=UTF-8",
    "Content-Transfer-Encoding: 8bit",
    "Auto-Submitted: auto-generated",
    "",
    body.replace(/\r?\n/g, "\r\n"),
  ].join("\r\n");
}

function singleLine(value) {
  return String(value || "").replace(/[\r\n]/g, "").trim();
}

function json(status, payload, headers = {}) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
      ...headers,
    },
  });
}
