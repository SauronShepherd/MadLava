import assert from "node:assert/strict";
import { createHash, webcrypto } from "node:crypto";
import fs from "node:fs";
import vm from "node:vm";

class FakeElement {
  constructor(id = "") {
    this.id = id;
    this.value = "";
    this.textContent = "";
    this.listeners = new Map();
  }
  addEventListener(type, callback) { this.listeners.set(type, callback); }
  dispatch(type) { return this.listeners.get(type)?.({ type, target: this }); }
}

const elements = new Map([
  ["export-filtered-records", new FakeElement("export-filtered-records")],
  ["export-filtered-provenance", new FakeElement("export-filtered-provenance")],
  ["raw-record-context", new FakeElement("raw-record-context")],
  ["raw-record-time-start", new FakeElement("raw-record-time-start")],
  ["raw-record-time-end", new FakeElement("raw-record-time-end")],
]);
elements.get("raw-record-time-start").value = "2026-09-04T10:00";
elements.get("raw-record-time-end").value = "2026-09-04T10:10";

const rawRecordFilter = new FakeElement("raw-record-filter");
rawRecordFilter.value = "method-trace";
const visibleRecords = [
  {
    source: "segments/segment-000001.jsonl",
    line: 7,
    kind: "method-trace",
    correlationKind: "timestamp",
    snapshotSequence: 3,
    snapshotTimestamp: Date.parse("2026-09-04T10:00:00Z"),
    value: { schemaVersion: 5, type: "method-call", timestamp: "2026-09-04T10:05:00Z", method: "example.Work.run" },
  },
  {
    source: "segments/segment-000001.jsonl",
    line: 8,
    kind: "method-trace",
    correlationKind: "timestamp",
    snapshotSequence: 3,
    snapshotTimestamp: Date.parse("2026-09-04T10:00:00Z"),
    value: { schemaVersion: 5, type: "method-call", timestamp: "2026-09-04T10:06:00Z", method: "example.Work.stop" },
  },
];

const blobs = new Map();
const downloads = [];
let nextBlob = 1;
const URL = {
  createObjectURL(blob) {
    const href = `blob:${nextBlob++}`;
    blobs.set(href, blob);
    return href;
  },
  revokeObjectURL(href) {
    assert.ok(blobs.has(href), "only created object URLs may be revoked");
  },
};
const document = {
  createElement(tag) {
    assert.equal(tag, "a");
    return {
      href: "",
      download: "",
      click() { downloads.push({ href: this.href, download: this.download }); },
    };
  },
};

const context = {
  Blob,
  TextEncoder,
  Uint8Array,
  crypto: webcrypto,
  document,
  URL,
  sourceName: "run-42.jsonl",
  visibleRecords,
  rawRecordFilter,
  $: id => elements.get(id),
};
vm.createContext(context);
vm.runInContext(fs.readFileSync("report-viewer/filtered-export.js", "utf8"), context, {
  filename: "report-viewer/filtered-export.js",
});

await elements.get("export-filtered-records").dispatch("click");
assert.equal(downloads.length, 1);
assert.equal(downloads[0].download, "run-42-filtered.jsonl");
const jsonl = await blobs.get(downloads[0].href).text();
const expectedJsonl = `${visibleRecords.map(record => JSON.stringify(record.value)).join("\n")}\n`;
assert.equal(jsonl, expectedJsonl, "filtered export must remain byte-deterministic JSONL");

await elements.get("export-filtered-provenance").dispatch("click");
assert.equal(downloads.length, 2);
assert.equal(downloads[1].download, "run-42-filtered-provenance.json");
const provenance = JSON.parse(await blobs.get(downloads[1].href).text());
const expectedDigest = createHash("sha256").update(expectedJsonl, "utf8").digest("hex");
assert.equal(provenance.filteredContentSha256, expectedDigest);
assert.equal(provenance.filteredContentBytes, Buffer.byteLength(expectedJsonl, "utf8"));
assert.equal(provenance.recordCount, 2);
assert.deepEqual(provenance.filters, {
  type: "method-trace",
  timeStart: "2026-09-04T10:00",
  timeEnd: "2026-09-04T10:10",
});
assert.deepEqual(
  provenance.records.map(record => ({ source: record.source, line: record.line, correlationKind: record.correlationKind })),
  [
    { source: "segments/segment-000001.jsonl", line: 7, correlationKind: "timestamp" },
    { source: "segments/segment-000001.jsonl", line: 8, correlationKind: "timestamp" },
  ],
);

console.log("Filtered report export integrity behavior: PASS");
