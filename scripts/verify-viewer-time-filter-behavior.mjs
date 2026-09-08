import fs from "node:fs";
import vm from "node:vm";
import assert from "node:assert/strict";

class FakeElement {
  constructor(id = "") {
    this.id = id;
    this.value = "";
    this.textContent = "";
    this.children = [];
    this.attributes = new Map();
    this.listeners = new Map();
    this.focused = false;
  }
  addEventListener(type, callback) { this.listeners.set(type, callback); }
  dispatch(type) { this.listeners.get(type)?.({ type, target: this }); }
  setAttribute(name, value) { this.attributes.set(name, value); }
  removeAttribute(name) { this.attributes.delete(name); }
  getAttribute(name) { return this.attributes.get(name); }
  replaceChildren(...children) { this.children = [...children]; }
  appendChild(child) { this.children.push(child); return child; }
  focus() { this.focused = true; }
}

const elements = new Map([
  ["raw-record-time-start", new FakeElement("raw-record-time-start")],
  ["raw-record-time-end", new FakeElement("raw-record-time-end")],
  ["clear-raw-record-time", new FakeElement("clear-raw-record-time")],
  ["raw-record-context", new FakeElement("raw-record-context")],
]);
const rawRecordSelector = new FakeElement("raw-record-selector");
const rawRecordFilter = new FakeElement("raw-record-filter");
rawRecordFilter.value = "runtime";

const records = [
  { kind: "runtime", source: "events.jsonl", line: 1, value: { timestamp: "2026-09-04T10:00:00Z" } },
  { kind: "runtime", source: "events.jsonl", line: 2, value: { timestamp: "2026-09-04T10:10:00Z" } },
  { kind: "spark", source: "events.jsonl", line: 3, value: { timestamp: "2026-09-04T10:05:00Z" } },
  { kind: "runtime", source: "events.jsonl", line: 4, value: {} },
];

const context = {
  console,
  Date,
  Number,
  Boolean,
  String,
  records,
  visibleRecords: [],
  rawRecordSelector,
  rawRecordFilter,
  renderRawRecord() {},
  recordTimestamp(value) {
    if (!value.timestamp) return null;
    const parsed = Date.parse(value.timestamp);
    return Number.isFinite(parsed) ? parsed : null;
  },
  populateRawRecords() {},
  document: { createElement: () => new FakeElement() },
  $: id => elements.get(id),
};
vm.createContext(context);
vm.runInContext(fs.readFileSync("report-viewer/time-filter.js", "utf8"), context, {
  filename: "report-viewer/time-filter.js",
});

const start = elements.get("raw-record-time-start");
const end = elements.get("raw-record-time-end");
const clear = elements.get("clear-raw-record-time");
const status = elements.get("raw-record-context");

start.value = "2026-09-04T10:00";
end.value = "2026-09-04T10:10";
start.dispatch("input");
assert.equal(context.visibleRecords.length, 2, "inclusive bounds should keep both runtime boundary records");
assert.deepEqual(context.visibleRecords.map(record => record.line), [1, 2]);

start.value = "not-a-date";
start.dispatch("input");
assert.equal(context.visibleRecords.length, 0, "malformed populated bound must fail closed");
assert.equal(start.getAttribute("aria-invalid"), "true");
assert.match(status.textContent, /Invalid start time bound/);

clear.dispatch("click");
assert.equal(start.value, "");
assert.equal(end.value, "");
assert.equal(start.getAttribute("aria-invalid"), undefined);
assert.equal(end.getAttribute("aria-invalid"), undefined);
assert.equal(start.focused, true, "clear should return keyboard focus to the start control");
assert.deepEqual(
  context.visibleRecords.map(record => record.line),
  [1, 2, 4],
  "clear should restore the active runtime type filter, including untimestamped records",
);
assert.match(status.textContent, /Time bounds cleared/);

start.value = "2026-09-04T10:11";
end.value = "2026-09-04T10:00";
end.dispatch("input");
assert.equal(context.visibleRecords.length, 0);
assert.equal(start.getAttribute("aria-invalid"), "true");
assert.equal(end.getAttribute("aria-invalid"), "true");
assert.match(status.textContent, /start must not be after the end/);

console.log("Offline viewer time-filter behavior: PASS");
