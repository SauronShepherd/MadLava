param([Parameter(Mandatory=$true)][string]$Report)
$ErrorActionPreference='Stop'
$path=(Resolve-Path -LiteralPath $Report).Path
$lines=@(Get-Content -LiteralPath $path | Where-Object {$_.Trim()})
if($lines.Count -eq 0){throw 'Report is empty'}
$previous=-1L;$index=0
foreach($line in $lines){
  $index++;try{$value=$line|ConvertFrom-Json}catch{throw "Malformed JSON at line $index"}
  if($value.schemaVersion -ne 1){throw "Unsupported schema version at line $index"}
  foreach($required in @('timestamp','agentVersion','configurationHash','pid','configuration')){if($null -eq $value.$required){throw "Missing $required at line $index"}}
  if($value.sequence -ne $null){if([long]$value.sequence -lt $previous){throw "Sequence regression at line $index"};$previous=[long]$value.sequence}
}
$manifest=Join-Path (Split-Path $path) 'madlava-report-manifest.json'
if(Test-Path -LiteralPath $manifest){
  $meta=Get-Content -Raw $manifest|ConvertFrom-Json
  $manifestTarget=$path
  if($meta.path){$candidate=[Environment]::ExpandEnvironmentVariables([string]$meta.path);if(Test-Path -LiteralPath $candidate){$manifestTarget=(Resolve-Path -LiteralPath $candidate).Path}}
  $actual=(Get-FileHash -LiteralPath $manifestTarget -Algorithm SHA256).Hash.ToLower()
  if($meta.sha256 -and $meta.sha256 -ne $actual){throw 'Report SHA-256 mismatch'}
}
Write-Output "Report valid: $path ($($lines.Count) records)"
