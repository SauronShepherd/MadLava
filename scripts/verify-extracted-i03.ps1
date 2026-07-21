param([string]$Revision='0.1.0-alpha.1')
$ErrorActionPreference='Stop'
$archive=(Resolve-Path "target/madlava-$Revision-complete-project.zip").Path
$root=Join-Path ([IO.Path]::GetTempPath()) "madlava-i03-extracted-$PID"
if(Test-Path $root){Remove-Item -LiteralPath $root -Recurse -Force}
Expand-Archive -LiteralPath $archive -DestinationPath $root
$project=Join-Path $root "madlava-$Revision-complete-project"
try{
  Push-Location $project
  $mvn='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd'
  & $mvn -B -Pgeneric package -DskipTests "-Drevision=$Revision"
  if($LASTEXITCODE){throw 'Extracted project build failed'}
  $output=Join-Path $project 'extracted-example-output';New-Item -ItemType Directory -Path $output|Out-Null
  $java=Join-Path $env:JAVA_HOME 'bin\java.exe'
  & $java "-javaagent:target/madlava-agent-$Revision.jar=output=$output,instrumentationInclude=example.app" -jar "target/madlava-agent-$Revision-example.jar"
  if($LASTEXITCODE){throw 'Extracted packaged example failed'}
  $report=Get-Content -Raw (Join-Path $output 'madlava.jsonl')
  if(!$report.Contains('"instanceCounting"') -or $report.Contains('MADLAVA_PACKAGED_SECRET_91827')){throw 'Extracted report validation failed'}
}finally{Pop-Location;if(Test-Path $root){Remove-Item -LiteralPath $root -Recurse -Force}}
Write-Output 'Extracted Iteration-03 project and example: PASS'
