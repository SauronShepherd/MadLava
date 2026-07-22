param([string]$Revision='0.1.0')
$ErrorActionPreference='Stop'
$readme=Get-Content -Raw README.md
foreach($required in @('https://medium.com/towards-data-engineering/madlava-feel-the-lava-ac32d4be8ad4','Codex and GPT-5.6','## Architecture','## Requirements and supported platforms','## Install and build','## Testing and certification','## Version 0.1 limitations',"## What's next",'report-viewer/sample/madlava-sample.jsonl')){if(!$readme.Contains($required)){throw "README missing: $required"}}
foreach($path in @('docs/architecture.md','docs/getting-started.md','docs/testing.md','report-viewer/README.md')){if(!(Test-Path -LiteralPath $path)){throw "Missing documentation: $path"};if((Get-Item -LiteralPath $path).Length-lt200){throw "Documentation is unexpectedly short: $path"}}
if($readme -notmatch [regex]::Escape("madlava-agent-$Revision.jar")){throw 'README release command is not normalized'}
Write-Output 'Iteration-08 documentation: PASS'
