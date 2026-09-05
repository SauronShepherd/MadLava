$ErrorActionPreference = 'Stop'
$html = Get-Content -Raw 'report-viewer/index.html'
$script = Get-Content -Raw 'report-viewer/filtered-export.js'

if ($html -notmatch 'id="export-filtered-records"') { throw 'Filtered export control is missing.' }
if ($html -notmatch 'id="export-filtered-provenance"') { throw 'Filtered provenance export control is missing.' }
if ($html -notmatch 'filtered-export\.js') { throw 'Filtered export script is not loaded.' }
if ($html -notmatch 'reopenable JSONL') { throw 'Filtered export semantics are not documented.' }
if ($html -notmatch 'source, line, type, and correlation context') { throw 'Provenance export semantics are not documented.' }
if ($script -notmatch 'visibleRecords\.map') { throw 'Export must serialize the active filtered record set.' }
if ($script -notmatch 'JSON\.stringify\(record\.value\)') { throw 'Export must preserve raw record payloads.' }
if ($script -notmatch 'application/x-ndjson') { throw 'Export must use JSONL/NDJSON content type.' }
if ($script -notmatch '-filtered\.jsonl') { throw 'Export filename must identify the filtered subset.' }
if ($script -notmatch '-filtered-provenance\.json') { throw 'Provenance export filename must be explicit.' }
if ($script -notmatch 'schemaVersion:\s*1') { throw 'Provenance manifest must be schema-versioned.' }
if ($script -notmatch 'timeStart:') { throw 'Provenance manifest must capture lower time bound.' }
if ($script -notmatch 'timeEnd:') { throw 'Provenance manifest must capture upper time bound.' }
if ($script -notmatch 'correlationKind: record\.correlationKind') { throw 'Provenance manifest must retain correlation evidence.' }
if ($script -notmatch 'source: record\.source') { throw 'Provenance manifest must retain source file.' }
if ($script -notmatch 'line: record\.line') { throw 'Provenance manifest must retain source line.' }
if ($script -notmatch 'URL\.revokeObjectURL') { throw 'Export object URLs must be released.' }
if ($script -match '\beval\s*\(' -or $script -match '\.innerHTML\s*=') { throw 'Filtered export must remain CSP-safe.' }

node --check report-viewer/filtered-export.js
if ($LASTEXITCODE -ne 0) { throw 'filtered-export.js failed syntax validation.' }
Write-Host 'Filtered report and provenance export contracts verified.'
