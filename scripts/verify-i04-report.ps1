param([string]$Revision='0.1.0-alpha.2')
$ErrorActionPreference='Stop';$report='target/packaged-agent-it/madlava.jsonl';if(!(Test-Path $report)){throw 'Packaged I04 report missing'};$text=Get-Content -Raw $report
foreach($required in @('"streamIo"','"observedLayers"','"physicalAggregation":false','"networkIo"','"endpointAnonymized":true','"serialization"','"byteAccuracy":"SOURCE_SPECIFIC"','"payloadCapture":false','"threadPools"','"submitted"','"completed"')){if(!$text.Contains($required)){throw "I04 report missing $required"}}
foreach($forbidden in @('MADLAVA_PACKAGED_SECRET_91827','customer.internal.example','payload-secret')){if($text.Contains($forbidden)){throw "I04 privacy leak: $forbidden"}}
Get-Content $report|ForEach-Object{$_|ConvertFrom-Json|Out-Null};Write-Output 'Iteration-04 packaged report: PASS'
