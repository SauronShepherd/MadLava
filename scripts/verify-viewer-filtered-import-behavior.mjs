import assert from "node:assert/strict";
import { createHash, webcrypto } from "node:crypto";
import fs from "node:fs";
import vm from "node:vm";

class FakeElement {
  constructor() { this.files = []; this.listeners = new Map(); }
  addEventListener(type, callback) { this.listeners.set(type, callback); }
  dispatch(type) { return this.listeners.get(type)?.({ type, target: this }); }
}

function file(name, content) {
  const bytes = new TextEncoder().encode(content);
  return {
    name,
    async text() { return content; },
    async arrayBuffer() { return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength); },
  };
}

const jsonl = '{"schemaVersion":4,"sequence":1,"final":true}\n';
const digest = createHash("sha256").update(jsonl, "utf8").digest("hex");
const provenance = JSON.stringify({
  schemaVersion: 1,
  recordCount: 1,
  filteredContentBytes: Buffer.byteLength(jsonl, "utf8"),
  filteredContentSha256: digest,
});
const elements = new Map([
  ["filtered-import-records", new FakeElement()],
  ["filtered-import-provenance", new FakeElement()],
  ["verify-filtered-import", new FakeElement()],
]);
elements.get("filtered-import-records").files = [file("run-filtered.jsonl", jsonl)];
elements.get("filtered-import-provenance").files = [file("run-filtered-provenance.json", provenance)];
const statusNode = { textContent: "" };
const opened = [];
const errors = [];
const context = {
  TextDecoder,
  Uint8Array,
  crypto: webcrypto,
  statusNode,
  $: id => elements.get(id),
  async loadReportFiles(files, name, integrity) { opened.push({ files, name, integrity }); },
  clearReport(error) { errors.push(error); },
};
vm.createContext(context);
vm.runInContext(fs.readFileSync("report-viewer/filtered-import.js", "utf8"), context, {
  filename: "report-viewer/filtered-import.js",
});
await elements.get("verify-filtered-import").dispatch("click");
assert.equal(errors.length, 0);
assert.equal(opened.length, 1);
assert.equal(opened[0].name, "run-filtered.jsonl");
assert.match(opened[0].integrity, /exact bytes and SHA-256/);

const tampered = '{"schemaVersion":4,"sequence":2,"final":true}\n';
elements.get("filtered-import-records").files = [file("run-filtered.jsonl", tampered)];
await elements.get("verify-filtered-import").dispatch("click");
assert.equal(opened.length, 1, "tampered JSONL must not be opened");
assert.equal(errors.length, 1);
assert.match(String(errors[0]?.message), /(byte count|SHA-256)/);

console.log("Filtered report import integrity behavior: PASS");
