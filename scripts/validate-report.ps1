param([Parameter(Mandatory=$true)][string]$Report)
$ErrorActionPreference='Stop'
$path=(Resolve-Path -LiteralPath $Report).Path
$lines=@(Get-Content -LiteralPath $path | Where-Object {$_.Trim()})
if($lines.Count -eq 0){throw 'Report is empty'}
$previous=-1L;$index=0;$snapshots=0;$events=0
foreach($line in $lines){
  $index++;try{$value=$line|ConvertFrom-Json}catch{throw "Malformed JSON at line $index"}
  $recordType=[string]$value.recordType
  if([string]::IsNullOrWhiteSpace($recordType)){
    if($value.schemaVersion -in @(1,3,4)){$recordType='snapshot'}
    elseif($value.schemaVersion -eq 5 -and $value.type -eq 'method-call'){$recordType='method-trace'}
    elseif($value.type -in @('configuration-change','configuration-change-rejected')){$recordType='configuration-change'}
    else{$recordType='unknown'}
  }
  switch($recordType){
    'snapshot' {
      if($value.schemaVersion -notin @(1,3,4)){throw "Unsupported snapshot schema version at line $index"}
      if($value.schemaVersion -eq 1){foreach($required in @('timestamp','agentVersion','configurationHash','pid','configuration')){if($null -eq $value.$required){throw "Missing $required at line $index"}}}
      if($value.sequence -ne $null){if([long]$value.sequence -lt $previous){throw "Sequence regression at line $index"};$previous=[long]$value.sequence}
      $snapshots++
    }
    'method-trace' {
      if($value.schemaVersion -ne 5){throw "Unsupported method trace schema version at line $index"}
      foreach($required in @('timestamp','method','durationNanos')){if($null -eq $value.$required){throw "Missing $required at line $index"}}
      $events++
    }
    'configuration-change' {
      foreach($required in @('timestamp','configurationVersion','type')){if($null -eq $value.$required){throw "Missing $required at line $index"}}
      if($value.type -notin @('configuration-change','configuration-change-rejected')){throw "Unsupported configuration event at line $index"}
      $events++
    }
    default {throw "Unsupported record type at line $index"}
  }
}
if($snapshots -eq 0){throw 'Report contains no snapshots'}
$manifest=Join-Path (Split-Path $path) 'madlava-report-manifest.json'
if(Test-Path -LiteralPath $manifest){
  $meta=Get-Content -Raw $manifest|ConvertFrom-Json
  $manifestTarget=$path
  if($meta.path){$candidate=[Environment]::ExpandEnvironmentVariables([string]$meta.path);if(Test-Path -LiteralPath $candidate){$manifestTarget=(Resolve-Path -LiteralPath $candidate).Path}}
  $actual=(Get-FileHash -LiteralPath $manifestTarget -Algorithm SHA256).Hash.ToLower()
  if($meta.sha256 -and $meta.sha256 -ne $actual -and -not $meta.segments){throw 'Report SHA-256 mismatch'}
}
Write-Output "Report valid: $path ($snapshots snapshots, $events events)"
