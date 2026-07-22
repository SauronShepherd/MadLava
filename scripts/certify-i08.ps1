param([string]$Revision='0.1.0',[switch]$CommittedHead)
$ErrorActionPreference='Stop'
$mvn='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven\lib\maven3\bin\mvn.cmd'
$lanes=@(
  @{Name='Java 11';Home='C:\Users\angel.alvarez\.jdks\jdk11.0.26_4';Full=$true},
  @{Name='Java 17';Home='C:\Users\angel.alvarez\.jdks\corretto-17.0.19';Full=$false},
  @{Name='Java 21';Home='C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot';Full=$false}
)
foreach($lane in $lanes){
  if(!(Test-Path (Join-Path $lane.Home 'bin\java.exe'))){throw "$($lane.Name) unavailable"}
  $env:JAVA_HOME=$lane.Home;$env:Path="$($lane.Home)\bin;$env:Path"
  if($lane.Full){& $mvn -B -Pgeneric clean verify "-Drevision=$Revision"}else{& $mvn -B -Pgeneric -Dtest='MadLavaAgentTest,JvmMetricsCollectorTest,RuntimeObservationBridgeTest,ConstructorThrowableInstrumentationTest' test "-Drevision=$Revision"}
  if($LASTEXITCODE){throw "$($lane.Name) compatibility lane failed"}
}
$env:JAVA_HOME=$lanes[0].Home;$env:Path="$env:JAVA_HOME\bin;$env:Path"
& scripts/inspect-agent-jar.ps1 -Revision $Revision
& scripts/verify-java11-bytecode.ps1 -Revision $Revision
& scripts/verify-viewer.ps1
& scripts/verify-artifacts.ps1 -Revision $Revision
& scripts/verify-i08-docs.ps1 -Revision $Revision
& scripts/verify-english.ps1
& scripts/verify-extracted-i04.ps1 -Revision $Revision
& scripts/generate-checksums.ps1 -Revision $Revision
$checksums="target/MadLava-$Revision-SHA256SUMS.txt"
if(!(Test-Path $checksums) -or (Get-Content $checksums).Count-ne4){throw 'Checksum manifest validation failed'}
if($CommittedHead -and (git status --short --untracked-files=no)){throw 'Committed HEAD has tracked changes'}
Write-Output 'I08 CERTIFICATION: PASS'
