"use strict";
(() => {
  const exportButton = $("export-filtered-records");
  const provenanceButton = $("export-filtered-provenance");

  function download(content, type, suffix) {
    const blob = new Blob([content], { type });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = `${sourceName.replace(/\.(jsonl|json)$/i, "")}${suffix}`;
    link.click();
    URL.revokeObjectURL(link.href);
  }

  function filteredJsonl() {
    return `${visibleRecords.map(record => JSON.stringify(record.value)).join("\n")}\n`;
  }

  async function sha256Text(content) {
    if (!globalThis.crypto?.subtle) {
      throw new Error("SHA-256 provenance is unavailable in this browser context.");
    }
    const bytes = new TextEncoder().encode(content);
    const digest = await crypto.subtle.digest("SHA-256", bytes);
    return Array.from(new Uint8Array(digest), value => value.toString(16).padStart(2, "0")).join("");
  }

  function exportVisibleRecords() {
    if (!visibleRecords.length) {
      $("raw-record-context").textContent = "No filtered records are available to export.";
      return;
    }
    download(filteredJsonl(), "application/x-ndjson", "-filtered.jsonl");
  }

  async function exportVisibleProvenance() {
    if (!visibleRecords.length) {
      $("raw-record-context").textContent = "No filtered records are available for provenance export.";
      return;
    }
    const payload = filteredJsonl();
    const encoded = new TextEncoder().encode(payload);
    const manifest = {
      schemaVersion: 1,
      source: sourceName,
      recordCount: visibleRecords.length,
      filteredContentBytes: encoded.byteLength,
      filteredContentSha256: await sha256Text(payload),
      filters: {
        type: rawRecordFilter.value,
        timeStart: $("raw-record-time-start").value || null,
        timeEnd: $("raw-record-time-end").value || null,
      },
      records: visibleRecords.map(record => ({
        source: record.source,
        line: record.line,
        kind: record.kind,
        correlationKind: record.correlationKind,
        snapshotSequence: record.snapshotSequence,
        snapshotTimestamp: record.snapshotTimestamp ?? null,
      })),
    };
    download(`${JSON.stringify(manifest, null, 2)}\n`, "application/json", "-filtered-provenance.json");
  }

  exportButton.addEventListener("click", exportVisibleRecords);
  provenanceButton.addEventListener("click", async () => {
    try {
      await exportVisibleProvenance();
    } catch (error) {
      $("raw-record-context").textContent = error instanceof Error ? error.message : "Unable to export provenance.";
    }
  });
})();
