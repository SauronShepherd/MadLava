param([string]$Revision='0.1.0')
$ErrorActionPreference='Stop'
$viewer="target/madlava-report-viewer-$Revision.zip"
$project="target/madlava-$Revision-complete-project.zip"
foreach($path in @($viewer,$project)){if(!(Test-Path -LiteralPath $path)){throw "Missing $path"}}
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Entries([string]$path){$zip=[IO.Compression.ZipFile]::OpenRead((Resolve-Path $path));try{@($zip.Entries|ForEach-Object FullName)}finally{$zip.Dispose()}}
$viewerEntries=Entries $viewer
foreach($required in @('index.html','viewer.css','viewer.js','sample/madlava-sample.jsonl')){if($viewerEntries -notcontains $required){throw "Viewer archive is missing $required"}}
$projectEntries=Entries $project
$projectRoot="madlava-$Revision-complete-project"
foreach($required in @("$projectRoot/pom.xml","$projectRoot/src/main/java/com/madlava/agent/MadLavaAgent.java","$projectRoot/report-viewer/index.html")){if($projectEntries -notcontains $required){throw "Project archive is missing $required"}}
if($projectEntries -match '(^|/)(\.git|\.idea|target)/|MadLava\.pptx|Technical-Specification-Updated'){throw 'Project archive contains a forbidden workspace file'}
$schema=Get-Content -Raw src/main/resources/schema/madlava-report-v1.schema.json|ConvertFrom-Json
if($schema.'$schema' -notmatch 'json-schema' -or $schema.properties.schemaVersion.const -ne 1){throw 'Schema-v1 metadata is invalid'}
$sums="target/MadLava-$Revision-SHA256SUMS.txt";if(!(Test-Path -LiteralPath $sums)){throw 'Checksum manifest missing'}
$provenance="target/MadLava-$Revision-provenance.json";if(!(Test-Path -LiteralPath $provenance)){throw 'Provenance manifest missing'}
foreach($line in Get-Content $sums){$parts=$line -split '\s+',2;$actual=(Get-FileHash -LiteralPath (Join-Path target $parts[1]) -Algorithm SHA256).Hash.ToLower();if($actual -ne $parts[0]){throw "Checksum mismatch: $($parts[1])"}}
Write-Output 'Packaged artifacts, schema-v1, checksums, and provenance: PASS'
