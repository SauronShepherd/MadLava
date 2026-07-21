param([string]$Revision='0.1.0-dev.2')
$ErrorActionPreference='Stop'
$report='target/packaged-agent-it/madlava.jsonl'
if(!(Test-Path -LiteralPath $report)){throw 'Packaged report is missing'}
$records=@(Get-Content -LiteralPath $report|ForEach-Object{$_|ConvertFrom-Json})
if(!$records.Count){throw 'Packaged report is empty'}
$final=$records|Where-Object{$_.snapshot.final}|Select-Object -Last 1
if(!$final){throw 'Final snapshot is missing'}
foreach($id in @('heapUsage','nonHeapUsage','bufferPools','garbageCollection','threadStatistics','threadCpu','processResources','classLoaderInsights','jvmExecutionEngine','selfObservability')){if(!($final.features.PSObject.Properties.Name -contains $id)){throw "Missing I02 feature $id"}}
if($final.agent.version -ne $Revision){throw 'Report version mismatch'}
Write-Output 'Iteration-02 packaged report: PASS'
