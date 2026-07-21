param([string]$Revision='0.1.0-alpha.1')
$ErrorActionPreference='Stop'
$report='target/packaged-agent-it/madlava.jsonl'
if(!(Test-Path $report)){throw 'Packaged Iteration-03 report missing'}
$text=Get-Content -Raw $report
foreach($required in @('"instanceCounting"','"successfulOutermostConstructors"','"throwables"','"created"','"explicitThrows"','"propagations"','"jfrState"','"messageCapture":false','ExampleApplication$ObservedException')){if(!$text.Contains($required)){throw "Report missing $required"}}
foreach($secret in @('MADLAVA_PACKAGED_SECRET_91827','failed-super-secret','post-init-secret')){if($text.Contains($secret)){throw "Privacy leak: $secret"}}
Get-Content $report | ForEach-Object { $_ | ConvertFrom-Json | Out-Null }
Write-Output 'Iteration-03 packaged report and privacy: PASS'
