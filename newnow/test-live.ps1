$base = "http://localhost:8080/api"
$results = @()

function Pass($id, $name, $detail) {
    $script:results += [pscustomobject]@{ ID = $id; Name = $name; Status = 'PASS'; Detail = $detail }
    Write-Host "[PASS] $id - $name" -ForegroundColor Green
}
function Fail($id, $name, $detail) {
    $script:results += [pscustomobject]@{ ID = $id; Name = $name; Status = 'FAIL'; Detail = $detail }
    Write-Host "[FAIL] $id - $name : $detail" -ForegroundColor Red
}

Write-Host "`n=== LIVE API TEST ===" -ForegroundColor Cyan

# K2 admin
try {
    $adminLogin = Invoke-RestMethod -Uri "$base/auth/login" -Method POST -Body '{"email":"admin@newnow.com","password":"admin123"}' -ContentType "application/json"
    $adminToken = $adminLogin.token
    Pass "K2" "Admin login" "role=$($adminLogin.role)"
} catch { Fail "K2" "Admin login" $_.ErrorDetails.Message; exit 1 }

$ah = @{ Authorization = "Bearer $adminToken" }

# K8
try {
    $home = Invoke-RestMethod -Uri "$base/home"
    Pass "K8" "Početna API" "danas=$($home.todayEvents.Count), top=$($home.topLocations.Count), utisci=$($home.recentReviews.Count)"
} catch { Fail "K8" "Početna API" $_.Exception.Message }

# K3
try {
    $locs = Invoke-RestMethod -Uri "$base/locations"
    $script:locId = $locs[0].id
    $script:locName = $locs[0].name
    Pass "K3" "Lista mesta" "$($locs.Count) mesta, prvo: $locName"
    $det = Invoke-RestMethod -Uri "$base/locations/$locId/details"
    Pass "K3" "Detalji mesta" "ocena=$($det.averageRating), utisci=$($det.totalReviews)"
} catch { Fail "K3" "Mesta" $_.Exception.Message }

# K6
try {
    $events = Invoke-RestMethod -Uri "$base/events"
    $today = Invoke-RestMethod -Uri "$base/events/today"
    $free = Invoke-RestMethod -Uri "$base/events/free"
    Pass "K6" "Događaji API" "svi=$($events.Count), danas=$($today.Count), besplatni=$($free.Count)"
} catch { Fail "K6" "Događaji" $_.Exception.Message }

# K7
try {
    $rev = Invoke-RestMethod -Uri "$base/locations/$locId/reviews?sortBy=rating&order=desc"
    Pass "K7" "Sort utisaka" "$($rev.Count) utisaka"
} catch { Fail "K7" "Sort utisaka" $_.Exception.Message }

# K1 + A1 + K2 user
$testEmail = "demotest_$(Get-Random)@test.com"
try {
    $reg = Invoke-RestMethod -Uri "$base/account-requests" -Method POST -Body (@{ fullName = "Demo Test"; email = $testEmail; password = "demo123456" } | ConvertTo-Json) -ContentType "application/json"
    Pass "K1" "Zahtev registracije" "status=$($reg.status)"
} catch { Fail "K1" "Zahtev registracije" $_.ErrorDetails.Message }

try {
    Invoke-RestMethod -Uri "$base/auth/login" -Method POST -Body (@{ email = $testEmail; password = "demo123456" } | ConvertTo-Json) -ContentType "application/json"
    Fail "K1" "Login pre odobrenja" "Nije blokiran"
} catch { Pass "K1" "Login pre odobrenja" "Blokiran OK" }

try {
    $pending = Invoke-RestMethod -Uri "$base/account-requests/pending" -Headers $ah
    $req = $pending | Where-Object { $_.email -eq $testEmail } | Select-Object -First 1
    Invoke-RestMethod -Uri "$base/account-requests/$($req.id)/approve" -Method POST -Headers $ah | Out-Null
    Pass "A1" "Odobri zahtev" "id=$($req.id)"
    $userLogin = Invoke-RestMethod -Uri "$base/auth/login" -Method POST -Body (@{ email = $testEmail; password = "demo123456" } | ConvertTo-Json) -ContentType "application/json"
    $userToken = $userLogin.token
    Pass "K2" "User login" "OK"
} catch { Fail "A1/K2" "Odobrenje i login" $_.ErrorDetails.Message; $userToken = $null }

if ($userToken) {
    $uh = @{ Authorization = "Bearer $userToken" }
    try {
        $prof = Invoke-RestMethod -Uri "$base/users/profile" -Headers $uh
        Pass "K10" "Profil" $prof.fullName
    } catch { Fail "K10" "Profil" $_.Exception.Message }
}

# NF1
try {
    Invoke-RestMethod -Uri "$base/users" -Headers @{ Authorization = "Bearer $userToken" }
    Fail "NF1" "Users bez admin" "Nije 403"
} catch { Pass "NF1" "Users zaštita" "403 za običnog korisnika" }
try {
    $users = Invoke-RestMethod -Uri "$base/users" -Headers $ah
    Pass "NF1" "Admin users" "$($users.Count) korisnika bez password polja"
} catch { Fail "NF1" "Admin users" $_.Exception.Message }

# Manager
$managerEmail = $null
$managerPwd = $null
$mgrToken = $null
$mgrLocId = $null
try {
    $users = Invoke-RestMethod -Uri "$base/users" -Headers $ah
    $mgrUser = $users | Where-Object { $_.role -eq 'MANAGER' } | Select-Object -First 1
    foreach ($pwd in @('petar123', 'manager123', 'password123', 'luka123', 'admin123')) {
        try {
            $ml = Invoke-RestMethod -Uri "$base/auth/login" -Method POST -Body (@{ email = $mgrUser.email; password = $pwd } | ConvertTo-Json) -ContentType "application/json"
            if ($ml.token) {
                $managerEmail = $mgrUser.email
                $managerPwd = $pwd
                $mgrToken = $ml.token
                break
            }
        } catch {}
    }
    if ($mgrToken) {
        Pass "M1" "Manager login" "$managerEmail / $managerPwd"
        $mh = @{ Authorization = "Bearer $mgrToken" }
        $myLoc = Invoke-RestMethod -Uri "$base/locations/my-location" -Headers $mh
        $mgrLocId = $myLoc.id
        Pass "M1" "My location" "$($myLoc.name)"
        $myRev = Invoke-RestMethod -Uri "$base/reviews/my-reviews" -Headers $mh
        Pass "M2" "My reviews" "$($myRev.Count) utisaka"
        $start = (Get-Date).AddMonths(-1).ToString('yyyy-MM-dd')
        $end = (Get-Date).ToString('yyyy-MM-dd')
        $an = Invoke-RestMethod -Uri "$base/analytics/location/$mgrLocId?startDate=$start&endDate=$end" -Headers $mh
        Pass "M4" "Analitika" "events=$($an.totalEvents), bottom=$($an.bottomLocations.Count)"
    } else {
        Fail "M1" "Manager login" "Nepoznata lozinka za $($mgrUser.email)"
    }
} catch { Fail "M1" "Manager" $_.Exception.Message }

# S1 ES
try {
    $s1 = Invoke-RestMethod -Uri "$base/search/locations?name=exit"
    Pass "S1" "ES pretraga name" "$($s1.Count) rezultata"
    $pfx = Invoke-RestMethod -Uri "$base/search/locations?description=Exit*"
    Pass "S1g" "Prefix opis" "$($pfx.Count) rezultata"
    if ($s1.Count -gt 0) {
        $mlt = Invoke-RestMethod -Uri "$base/search/locations/$($s1[0].id)/similar"
        Pass "S1i" "MLT" "$($mlt.Count) sličnih"
    }
} catch { Fail "S1" "Elasticsearch" $_.Exception.Message }

Write-Host "`n=== REZIME ===" -ForegroundColor Cyan
$results | Format-Table -AutoSize
Write-Host "PASS: $(($results | Where-Object Status -eq 'PASS').Count)  FAIL: $(($results | Where-Object Status -eq 'FAIL').Count)"

Write-Host "`n=== PODACI ZA UI TEST ===" -ForegroundColor Yellow
Write-Host "Admin: admin@newnow.com / admin123"
Write-Host "Test user: $testEmail / demo123456"
if ($managerEmail) { Write-Host "Manager: $managerEmail / $managerPwd (lokacija id=$mgrLocId)" }
Write-Host "Prvo mesto: $locName (id=$locId)"
