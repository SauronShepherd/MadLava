param([string]$Revision='0.1.0')
$ErrorActionPreference='Stop';$expected=@("madlava-agent-$Revision.jar","madlava-report-viewer-$Revision.zip","madlava-$Revision-complete-project.zip","madlava-agent-$Revision-example.jar")
$files=@($expected|ForEach-Object{Get-Item -LiteralPath (Join-Path target $_) -ErrorAction Stop}|Sort-Object Name);if($files.Count-ne$expected.Count){throw "Expected $($expected.Count) release artifacts"}
$lines=$files|ForEach-Object{"$((Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLower())  $($_.Name)"};$path="target/MadLava-$Revision-SHA256SUMS.txt";[IO.File]::WriteAllLines((Join-Path (Get-Location) $path),$lines,(New-Object Text.UTF8Encoding($false)));Write-Output $path
