$logPath = "C:\Users\JGJua\.gemini\antigravity-ide\brain\4788e569-03fb-490b-b840-de7e013ce12b\.system_generated\logs\transcript.jsonl"
$lines = Get-Content $logPath
foreach ($line in $lines) {
    if ($line.Contains('"step_index":5604,')) {
        $json = ConvertFrom-Json $line
        $clean = $line -replace '[^\x20-\x7E\n]', '.'
        if ($clean.Length -gt 6000) {
            Write-Host $clean.Substring(0, 6000)
            Write-Host "... [TRUNCATED] ..."
        } else {
            Write-Host $clean
        }
        break
    }
}
