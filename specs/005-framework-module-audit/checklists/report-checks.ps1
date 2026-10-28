# Checks C-01..C-06 for specs/005-framework-module-audit/review-report.md
# Usage: powershell -File specs/005-framework-module-audit/checklists/report-checks.ps1
$ErrorActionPreference = 'Stop'
$featureDir = Split-Path -Parent $PSScriptRoot
$reportPath = Join-Path $featureDir 'review-report.md'
$ledgerPath = Join-Path $featureDir 'coverage-ledger.md'
$specPath = Join-Path $featureDir 'spec.md'
$failed = 0

function Check([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) { Write-Output "PASS $name" }
    else { Write-Output "FAIL $name :: $detail"; $script:failed++ }
}

if (-not (Test-Path $reportPath)) { Write-Output 'FAIL report missing'; exit 1 }
$report = Get-Content $reportPath -Raw -Encoding UTF8

# C-01 evidence E-01..E-22 and findings F-01..F-21 present
$missingEvidence = @(); 1..25 | ForEach-Object { $id = 'E-{0:d2}' -f $_; if ($report -notmatch [regex]::Escape($id)) { $missingEvidence += $id } }
Check 'C-01a evidence E-01..E-25' ($missingEvidence.Count -eq 0) ($missingEvidence -join ',')

$findingHeadings = ([regex]::Matches($report, '(?m)^### F-(\d{2})\b')).Count
Check 'C-01b finding headings >= 21' ($findingHeadings -ge 21) "found=$findingHeadings"

# C-02 every finding section carries the 10 required labels
$requiredLabels = @('维度', '判定', '位置', '触发条件', '影响', '证据', '复现', '回归测试设计', '兼容性影响', '修复方向', '状态')
$blocks = [regex]::Split($report, '(?m)^### F-\d{2}\b') | Select-Object -Skip 1
$badBlocks = 0
foreach ($b in $blocks) {
    $head = ($b -split '(?m)^## ')[0]
    foreach ($label in $requiredLabels) {
        if ($head -notmatch [regex]::Escape($label)) { $badBlocks++ }
    }
}
Check 'C-02 finding fields complete' ($badBlocks -eq 0) "missing_label_count=$badBlocks"

# C-03 every HIGH finding references SOURCE_FACT or EXECUTABLE
$highBlocks = [regex]::Matches($report, '(?ms)^### F-\d{2} \[HIGH\].*?(?=^### F-\d{2}|^## )')
$weakHigh = 0
foreach ($m in $highBlocks) {
    if ($m.Value -notmatch 'SOURCE_FACT|EXECUTABLE') { $weakHigh++ }
}
Check 'C-03 high findings have strong evidence' ($weakHigh -eq 0) "weak=$weakHigh"

# C-04 six performance path keywords present
$paths = @('XSS_REWRITE', 'BODY_CACHE', 'RATE_LIMIT', 'CONCURRENT_TOOLS', 'EVENT_DISPATCH', 'REFLECTION_COPY')
$missingPaths = $paths | Where-Object { $report -notmatch [regex]::Escape($_) }
Check 'C-04 six performance paths' ($missingPaths.Count -eq 0) ($missingPaths -join ',')

# C-05 GateDecision blockers equals HIGH + DEFECT set
$highDefect = [regex]::Matches($report, '(?ms)^### F-(\d{2}) \[HIGH\]\s*\n\s*\n?- \*\*维度\*\*:[^\n]*\n(?:- \*\*[^\n]*\n)*?- \*\*判定\*\*: DEFECT')
$highDefectIds = @()
foreach ($m in [regex]::Matches($report, '(?ms)^### F-(\d{2}) \[HIGH\].*?(?=^### F-\d{2}|^## )')) {
    if ($m.Value -match '判定\*\*: DEFECT') { $highDefectIds += "F-$($m.Groups[1].Value)" }
}
$gateSection = if ($report -match '(?ms)## 准入门槛结论(.*)$') { $Matches[1] } else { '' }
$blockersLine = ($gateSection -split "`n" | Where-Object { $_ -match 'blockers|高危缺陷数' }) -join ' '
$missingBlockers = $highDefectIds | Where-Object { $blockersLine -notmatch [regex]::Escape($_) }
Check 'C-05 gate blockers cover HIGH+DEFECT' ($missingBlockers.Count -eq 0) ("expected=" + ($highDefectIds -join ',') + "; missing=" + ($missingBlockers -join ','))

# C-06 no absolute elapsed-time assertions in the report
$elapsed = [regex]::Matches($report, '(?i)(断言|assert)[^\n]{0,40}<\s*\d+\s*ms')
Check 'C-06 no elapsed-time assertion' ($elapsed.Count -eq 0) "matches=$($elapsed.Count)"

# C-07 top-level chapters 1..14 in order
$chapters = [regex]::Matches($report, '(?m)^## (\d{1,2})\.') | ForEach-Object { [int]$_.Groups[1].Value }
$expectedChapters = 1..14
$chapterOk = ($chapters.Count -eq $expectedChapters.Count) -and (($chapters -join ',') -eq ($expectedChapters -join ','))
Check 'C-07 chapter numbering 1..14' $chapterOk ("actual=" + ($chapters -join ','))

# C-08 status field valid and consistent with tracking table
$allowedStatus = @('DELIVERED', 'REPRODUCED', 'UNVERIFIED_ASSUMPTION')
$findingStatus = @{}
$statusMismatch = @()
foreach ($m in [regex]::Matches($report, '(?ms)^### F-(\d{2}) .*?(?=^### F-\d{2}|^## )')) {
    $id = 'F-' + $m.Groups[1].Value
    $s = [regex]::Match($m.Value, '(?m)^- \*\*状态\*\*: (\w+)')
    if ($s.Success) { $findingStatus[$id] = $s.Groups[1].Value } else { $statusMismatch += "${id}:missing" }
}
$tableStatus = @{}
foreach ($t in [regex]::Matches($report, '(?m)^\| (F-\d{2}) \| (?:HIGH|MEDIUM|LOW) \| ([A-Z_]+) \|')) {
    $tableStatus[$t.Groups[1].Value] = $t.Groups[2].Value
}
foreach ($id in $findingStatus.Keys) {
    if ($allowedStatus -notcontains $findingStatus[$id]) { $statusMismatch += "$id:invalid" }
    if ($tableStatus.ContainsKey($id) -and $tableStatus[$id] -ne $findingStatus[$id]) { $statusMismatch += "${id}:$($findingStatus[$id])!=$($tableStatus[$id])" }
}
Check 'C-08 status values consistent' (($statusMismatch.Count -eq 0) -and ($findingStatus.Count -ge 21)) ("parsed=" + $findingStatus.Count + "; issues=" + ($statusMismatch -join ','))

# Bonus: ledger row count and finding coverage
if (Test-Path $ledgerPath) {
    $ledger = Get-Content $ledgerPath -Raw -Encoding UTF8
    $rows = ([regex]::Matches($ledger, '(?m)^\| `[^`]+\.java`')).Count
    Check 'L-01 ledger rows == 309' ($rows -eq 309) "rows=$rows"
    $missingInLedger = 1..21 | ForEach-Object { 'F-{0:d2}' -f $_ } | Where-Object { $ledger -notmatch [regex]::Escape($_) }
    Check 'L-02 ledger references all findings' ($missingInLedger.Count -eq 0) ($missingInLedger -join ',')
} else {
    Check 'L-01 ledger exists' $false 'coverage-ledger.md missing'
}

# C-10 findings ordered by severity desc, then id asc
$order = @()
foreach ($m in [regex]::Matches($report, '(?m)^### F-(\d{2}) \[(HIGH|MEDIUM|LOW)\]')) {
    $order += [pscustomobject]@{ Id = [int]$m.Groups[1].Value; Sev = $m.Groups[2].Value }
}
$rank = @{ HIGH = 0; MEDIUM = 1; LOW = 2 }
$orderOk = $true
$bad = ''
for ($i = 1; $i -lt $order.Count; $i++) {
    $prev = $order[$i - 1]
    $cur = $order[$i]
    if (($rank[$cur.Sev] -lt $rank[$prev.Sev]) -or (($rank[$cur.Sev] -eq $rank[$prev.Sev]) -and ($cur.Id -lt $prev.Id))) {
        $orderOk = $false
        $bad = "F-$('{0:d2}' -f $prev.Id)($($prev.Sev))->F-$('{0:d2}' -f $cur.Id)($($cur.Sev))"
        break
    }
}
Check 'C-10 findings ordered by severity' (($order.Count -ge 21) -and $orderOk) ("count=$($order.Count); bad=$bad")

# C-11 reproduction index covers every finding
$indexStart = $report.IndexOf('## 14. ')
$indexSection = if ($indexStart -ge 0) { $report.Substring($indexStart) } else { '' }
$indexIds = @()
foreach ($row in [regex]::Matches($indexSection, '(?m)^\| ([^|]*F-\d{2}[^|]*) \|')) {
    foreach ($idm in [regex]::Matches($row.Groups[1].Value, 'F-\d{2}')) { $indexIds += $idm.Value }
}
$indexIds = $indexIds | Sort-Object -Unique
$missingInIndex = @($findingStatus.Keys | Where-Object { $indexIds -notcontains $_ })
Check 'C-11 repro index covers all findings' ($missingInIndex.Count -eq 0) ("missing=" + ($missingInIndex -join ','))

# C-12 audit test count in chapter 2 ("8 个类 / N 个用例") equals chapter 14 ("8 类 M 用例")
# Chinese labels are written as \uXXXX escapes so the check survives encoding changes.
$ch2Match = [regex]::Match($report, '8 \u4e2a\u7c7b\s*/\s*(\d+)\s*\u4e2a\u7528\u4f8b')
$ch14Match = [regex]::Match($report, '8 \u7c7b\s+(\d+)\s*\u7528\u4f8b')
$ch2Num = if ($ch2Match.Success) { [int]$ch2Match.Groups[1].Value } else { -1 }
$ch14Num = if ($ch14Match.Success) { [int]$ch14Match.Groups[1].Value } else { -1 }
Check 'C-12 audit test counts consistent' (($ch2Num -gt 0) -and ($ch2Num -eq $ch14Num)) "ch2=$ch2Num; ch14=$ch14Num"

if (Test-Path $specPath) {
    $spec = Get-Content $specPath -Raw -Encoding UTF8
    $specIds = [regex]::Matches($spec, '\*\*(F-\d{2})') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
    $missingInReport = $specIds | Where-Object { $report -notmatch [regex]::Escape($_) }
    Check 'C-09 spec findings echoed in report' ($missingInReport.Count -eq 0) ($missingInReport -join ',')
}

# C-13 context matrix (chapter 6): OK rows with ASYNC_EXECUTOR/FORK_JOIN/PARALLEL_STREAM
# must cite executable evidence (Class#method) per data-model.md section 3 invariant.
$matrixStart = $report.IndexOf('## 6. ')
$matrixEnd = $report.IndexOf('## 7. ')
$matrixSection = if (($matrixStart -ge 0) -and ($matrixEnd -gt $matrixStart)) { $report.Substring($matrixStart, $matrixEnd - $matrixStart) } else { '' }
$weakMatrix = 0
$badMatrixRows = 0
if ($matrixSection -ne '') {
    foreach ($line in ($matrixSection -split "`r?`n")) {
        if ($line -notmatch '^\|') { continue }
        $cells = @($line -split '\|')
        # 8 columns + leading/trailing split artifacts => exactly 10 parts; anything else
        # means the matrix schema drifted and must fail loudly instead of being skipped.
        if ($cells.Count -ne 10) { $badMatrixRows++; continue }
        $propagate = $cells[3].Trim()
        $verdict = $cells[6].Trim()
        $evidence = $cells[8].Trim()
        if (($propagate -match 'ASYNC_EXECUTOR|FORK_JOIN|PARALLEL_STREAM') -and ($verdict -match '^OK') -and ($evidence -notmatch '#')) { $weakMatrix++ }
    }
}
Check 'C-13 matrix OK-async rows cite evidence' (($matrixSection -ne '') -and ($weakMatrix -eq 0) -and ($badMatrixRows -eq 0)) "weak=$weakMatrix badRows=$badMatrixRows"

Write-Output "----"
if ($failed -eq 0) { Write-Output 'RESULT: ALL CHECKS PASSED'; exit 0 }
Write-Output "RESULT: $failed CHECK(S) FAILED"; exit 1
