"use strict";
(() => {
  const startInput = $("raw-record-time-start");
  const endInput = $("raw-record-time-end");
  const basePopulateRawRecords = populateRawRecords;

  function boundTimestamp(input) {
    if (!input.value) return null;
    const timestamp = Date.parse(input.value);
    return Number.isFinite(timestamp) ? timestamp : null;
  }

  function recordWithinTimeRange(record, start, end, active) {
    if (!active) return true;
    const timestamp = recordTimestamp(record.value);
    if (timestamp === null) return false;
    if (start !== null && timestamp < start) return false;
    if (end !== null && timestamp > end) return false;
    return true;
  }

  function applyTimeRangeFilter() {
    const start = boundTimestamp(startInput);
    const end = boundTimestamp(endInput);
    const active = Boolean(startInput.value || endInput.value);
    const kind = rawRecordFilter.value;

    if (start !== null && end !== null && start > end) {
      visibleRecords = [];
      rawRecordSelector.replaceChildren();
      rawRecordSelector.value = "";
      renderRawRecord(-1);
      $("raw-record-context").textContent = "Invalid time range: the start must not be after the end.";
      return;
    }

    visibleRecords = records.filter(record =>
      (kind === "all" || record.kind === kind) && recordWithinTimeRange(record, start, end, active)
    );
    rawRecordSelector.replaceChildren();
    visibleRecords.forEach((record, index) => {
      const option = document.createElement("option");
      option.value = String(index);
      let context = "";
      if (record.kind !== "snapshot") {
        if (record.correlationKind === "timestamp" && record.snapshotSequence) context = ` · at/before #${record.snapshotSequence}`;
        else if (record.correlationKind === "stream" && record.snapshotSequence) context = ` · stream after #${record.snapshotSequence}`;
        else if (record.correlationKind === "timestamp-unmatched") context = " · timestamp uncorrelated";
      }
      option.textContent = `${index + 1}. ${record.kind}${context} - ${record.source}:${record.line}`;
      rawRecordSelector.appendChild(option);
    });
    rawRecordSelector.value = visibleRecords.length ? String(visibleRecords.length - 1) : "";
    renderRawRecord(visibleRecords.length - 1);
    if (active && !visibleRecords.length) {
      $("raw-record-context").textContent = "No timestamped records match the active type and inclusive time range.";
    }
  }

  populateRawRecords = function populateRawRecordsWithTimeRange() {
    basePopulateRawRecords();
    if (startInput.value || endInput.value) applyTimeRangeFilter();
  };

  startInput.addEventListener("input", applyTimeRangeFilter);
  endInput.addEventListener("input", applyTimeRangeFilter);
})();
