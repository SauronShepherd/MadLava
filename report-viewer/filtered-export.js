"use strict";
(() => {
  const exportButton = $("export-filtered-records");

  function exportVisibleRecords() {
    if (!visibleRecords.length) {
      $("raw-record-context").textContent = "No filtered records are available to export.";
      return;
    }
    const payload = `${visibleRecords.map(record => JSON.stringify(record.value)).join("\n")}\n`;
    const blob = new Blob([payload], { type: "application/x-ndjson" });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = `${sourceName.replace(/\.(jsonl|json)$/i, "")}-filtered.jsonl`;
    link.click();
    URL.revokeObjectURL(link.href);
  }

  exportButton.addEventListener("click", exportVisibleRecords);
})();
