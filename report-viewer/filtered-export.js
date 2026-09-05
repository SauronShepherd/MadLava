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

  function exportVisibleRecords() {
    if (!visibleRecords.length) {
      $("raw-record-context").textContent = "No filtered records are available to export.";
      return;
    }
    const payload = `${visibleRecords.map(record => JSON.stringify(record.value)).join("\n")}\n`;
    download(payload, "application/x-ndjson", "-filtered.jsonl");
  }

  function exportVisibleProvenance() {
    if (!visibleRecords.length) {
      $("raw-record-context").textContent = "No filtered records are available for provenance export.";
      return;
    }
    const manifest = {
      schemaVersion: 1,
      source: sourceName,
      recordCount: visibleRecords.length,
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
  provenanceButton.addEventListener("click", exportVisibleProvenance);
})();
