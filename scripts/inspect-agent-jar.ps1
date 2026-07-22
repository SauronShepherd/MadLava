param([string]$Revision='0.1.0')
$ErrorActionPreference='Stop';$jar="target/madlava-agent-$Revision.jar"
if(!(Test-Path -LiteralPath $jar)){throw "Missing $jar"};if(!$env:JAVA_HOME){throw 'JAVA_HOME is required'}
$jarTool=Join-Path $env:JAVA_HOME 'bin\jar.exe';if(!(Test-Path -LiteralPath $jarTool)){throw "JAR tool unavailable: $jarTool"}
$entries=& $jarTool tf $jar;if($LASTEXITCODE){throw 'Unable to list agent archive'}
if($entries -notcontains 'com/madlava/agent/MadLavaAgent.class'){throw 'Premain class absent'}
if($entries -match '^org/apache/spark/|^scala/|^py4j/|^com/esotericsoftware/kryo/|^org/junit/|^org/openjdk/jmh/|^org/slf4j/|^ch/qos/logback/'){throw 'Forbidden dependency in agent'}
if(!($entries -match '^com/madlava/internal/asm/ClassReader.class$')){throw 'Relocated ASM absent'}
if($entries -match '^org/objectweb/asm/'){throw 'Unrelocated ASM present'};if($entries -match 'CheckClassAdapter|Analyzer.class'){throw 'Test-only ASM utilities present'}
& $jarTool xf $jar 'META-INF/MANIFEST.MF'
try{$manifest=Get-Content -Raw 'META-INF/MANIFEST.MF';if($manifest -notmatch 'Premain-Class: com.madlava.agent.MadLavaAgent'){throw 'Premain-Class absent'};if($manifest -notmatch 'Agent-Class: com.madlava.agent.MadLavaAgent'){throw 'Agent-Class absent'};if($manifest -notmatch 'Can-Retransform-Classes: false'){throw 'Retransform manifest mismatch'};if($manifest -notmatch "Implementation-Version: $([regex]::Escape($Revision))"){throw 'Implementation-Version mismatch'}}finally{if(Test-Path -LiteralPath META-INF){Remove-Item -LiteralPath META-INF -Recurse -Force}}
Write-Output 'Agent archive: PASS'
