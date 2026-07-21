$ErrorActionPreference='Stop'
$roots=@('src','report-viewer','docs','README.md','CHANGELOG.md','CONTRIBUTING.md','CODE_OF_CONDUCT.md','SECURITY.md','THIRD-PARTY-LICENSES.md')
$files=Get-ChildItem -LiteralPath $roots -Recurse -File
foreach($file in $files){$text=Get-Content -Raw -LiteralPath $file.FullName;if($text -match '[^\x00-\x7F]'){throw "Non-ASCII source or message text: $($file.FullName)"}}
Write-Output 'English-only source and messages: PASS'
