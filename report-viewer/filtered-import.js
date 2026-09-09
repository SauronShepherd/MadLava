"use strict";
(() => {
  const recordsInput = $("filtered-import-records");
  const provenanceInput = $("filtered-import-provenance");
  const importButton = $("verify-filtered-import");

  async function sha256Bytes(bytes) {
    if (!globalThis.crypto?.subtle) {
      throw new Error("SHA-256 verification is unavailable in this browser context.");
    }
    const digest = await crypto.subtle.digest("SHA-256", bytes);
    return Array.from(new Uint8Array(digest), value => value.toString(16).padStart(2, "0")).join("");
  }

  async function verifyAndOpen() {
    const recordsFile = recordsInput.files?.[0];
    const provenanceFile = provenanceInput.files?.[0];
    if (!recordsFile || !provenanceFile) {
      throw new Error("Select both filtered JSONL and its provenance manifest.");
    }
    let manifest;
    try {
      manifest = JSON.parse(await provenanceFile.text());
    } catch (ignored) {
      throw new Error("Filtered provenance is not valid JSON.");
    }
    if (manifest?.schemaVersion !== 1) {
      throw new Error("Unsupported filtered provenance schema version.");
    }
    const bytes = new Uint8Array(await recordsFile.arrayBuffer());
    if (bytes.byteLength !== Number(manifest.filteredContentBytes)) {
      throw new Error("Filtered JSONL byte count does not match provenance.");
    }
    const digest = await sha256Bytes(bytes);
    if (digest !== String(manifest.filteredContentSha256 || "").toLowerCase()) {
      throw new Error("Filtered JSONL SHA-256 does not match provenance.");
    }
    const text = new TextDecoder().decode(bytes);
    const recordCount = text.split(/\r?\n/).filter(line => line.trim()).length;
    if (recordCount !== Number(manifest.recordCount)) {
      throw new Error("Filtered JSONL record count does not match provenance.");
    }
    await loadReportFiles(
      [recordsFile],
      recordsFile.name,
      `Filtered provenance verified: ${recordCount} record(s), exact bytes and SHA-256.`,
    );
  }

  importButton.addEventListener("click", async () => {
    statusNode.textContent = "Verifying filtered report provenance...";
    try {
      await verifyAndOpen();
    } catch (error) {
      clearReport(error);
    }
  });
})();
