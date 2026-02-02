$Out = "workflow"
$nl  = "`r`n"

Get-ChildItem -File -Recurse -Filter *.yml |
  Sort-Object FullName |
  ForEach-Object {
    "===== $($_.FullName) =====$nl" +
    (Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8) +
    $nl + $nl
  } |
  Set-Content -LiteralPath $Out -Encoding UTF8
