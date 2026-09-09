$ErrorActionPreference = 'Stop'
$html = Get-Content -Raw 'report-viewer/index.html'
$script = Get-Content -Raw 'report-viewer/filtered-export.js'
$importScript = Get-Content -Raw 'report-viewer/filtered-import.js'

if ($html -notmatch 'id="export-filtered-records"') { throw 'Filtered export control is missing.' }
if ($html -notmatch 'id="export-filtered-provenance"') { throw 'Filtered provenance export control is missing.' }
if ($html -notmatch 'filtered-export\.js') { throw 'Filtered export script is not loaded.' }
if ($html -notmatch 'id="filtered-import-records"') { throw 'Filtered JSONL reopen control is missing.' }
if ($html -notmatch 'id="filtered-import-provenance"') { throw 'Filtered provenance reopen control is missing.' }
if ($html -notmatch 'id="verify-filtered-import"') { throw 'Filtered integrity verification action is missing.' }
if ($html -notmatch 'filtered-import\.js') { throw 'Filtered import script is not loaded.' }
if ($html -notmatch 'reopenable JSONL') { throw 'Filtered export semantics are not documented.' }
if ($html -notmatch 'source, line, type, and correlation context') { throw 'Provenance export semantics are not documented.' }
if ($script -notmatch 'visibleRecords\.map') { throw 'Export must serialize the active filtered record set.' }
if ($script -notmatch 'JSON\.stringify\(record\.value\)') { throw 'Export must preserve raw record payloads.' }
if ($script -notmatch 'application/x-ndjson') { throw 'Export must use JSONL/NDJSON content type.' }
if ($script -notmatch '-filtered\.jsonl') { throw 'Export filename must identify the filtered subset.' }
if ($script -notmatch '-filtered-provenance\.json') { throw 'Provenance export filename must be explicit.' }
if ($script -notmatch 'schemaVersion:\s*1') { throw 'Provenance manifest must be schema-versioned.' }
if ($script -notmatch 'filteredContentBytes:') { throw 'Provenance manifest must record exact filtered JSONL byte count.' }
if ($script -notmatch 'filteredContentSha256:') { throw 'Provenance manifest must checksum the exact filtered JSONL content.' }
if ($script -notmatch 'crypto\.subtle\.digest\("SHA-256"') { throw 'Filtered export checksum must use browser SHA-256.' }
if ($script -notmatch 'timeStart:') { throw 'Provenance manifest must capture lower time bound.' }
if ($script -notmatch 'timeEnd:') { throw 'Provenance manifest must capture upper time bound.' }
if ($script -notmatch 'correlationKind: record\.correlationKind') { throw 'Provenance manifest must retain correlation evidence.' }
if ($script -notmatch 'source: record\.source') { throw 'Provenance manifest must retain source file.' }
if ($script -notmatch 'line: record\.line') { throw 'Provenance manifest must retain source line.' }
if ($script -notmatch 'URL\.revokeObjectURL') { throw 'Export object URLs must be released.' }
if ($script -match '\beval\s*\(' -or $script -match '\.innerHTML\s*=') { throw 'Filtered export must remain CSP-safe.' }
if ($importScript -notmatch 'filteredContentBytes') { throw 'Filtered reopen must verify exact byte count.' }
if ($importScript -notmatch 'filteredContentSha256') { throw 'Filtered reopen must verify the exported SHA-256.' }
if ($importScript -notmatch 'recordCount') { throw 'Filtered reopen must verify record count.' }
if ($importScript -notmatch 'loadReportFiles') { throw 'Verified filtered content must reopen through the normal parser.' }
if ($importScript -match '\beval\s*\(' -or $importScript -match '\.innerHTML\s*=') { throw 'Filtered import must remain CSP-safe.' }

node --check report-viewer/filtered-export.js
if ($LASTEXITCODE -ne 0) { throw 'filtered-export.js failed syntax validation.' }
node --check report-viewer/filtered-import.js
if ($LASTEXITCODE -ne 0) { throw 'filtered-import.js failed syntax validation.' }
node scripts/verify-viewer-filtered-export-behavior.mjs
if ($LASTEXITCODE -ne 0) { throw 'Filtered export behavior harness failed.' }
node scripts/verify-viewer-filtered-import-behavior.mjs
if ($LASTEXITCODE -ne 0) { throw 'Filtered import behavior harness failed.' }
Write-Host 'Filtered report export, provenance, and verified reopen contracts verified.'
