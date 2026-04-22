param(
    [ValidateSet('local-pg','tc')]
    [string]$Mode = 'local-pg',
    [switch]$Force
)

function Run-DockerExecOrFail($container, $user, $db, $sql) {
    Write-Host "Pr�buj� wykona� docker exec na kontenerze: $container"
    $cmd = "docker exec -i $container psql -U $user -d $db -c \"$sql\""
    $rc = & cmd /c $cmd 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "OK: wykonano w kontenerze $container"
        return $true
    } else {
        Write-Host "docker exec NIE powiod� si�: $rc"
        return $false
    }
}

# SQL który usuwa i odtwarza schemat public (usuwa WSZYSTKIE tabele i dane)
$sql = "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

if (-not $Force) {
    Write-Host "UWAGA: To usunie WSZYSTKIE dane w bazie testowej!" -ForegroundColor Yellow
    $answer = Read-Host "Kontynuowa�? (y/N)"
    if ($answer -ne 'y' -and $answer -ne 'Y') {
        Write-Host "Przerwano przez u�ytkownika."; exit 1
    }
}

if ($Mode -eq 'tc') {
    # kontener uruchamiany przez BaseIntegrationSpec
    $container = 'smartfin-test-pg'
    $user = 'test'
    $db = 'testdb'

    if (Run-DockerExecOrFail $container $user $db $sql) { exit 0 }

    Write-Host "Nie uda�o si� wykona� przez docker exec. Upewnij si�, �e kontener istnieje lub uruchom go ręcznie." -ForegroundColor Red
    Write-Host "Polecenie kt�re mo�esz uruchomi� r�cznie:" -ForegroundColor Cyan
    Write-Host "docker exec -i $container psql -U $user -d $db -c \"$sql\""
    exit 2
} else {
    # local-pg (docker-compose or local psql)
    $container = 'smartfin-postgres'
    $user = 'finuser'
    $db = 'smartfin_test'

    if (Run-DockerExecOrFail $container $user $db $sql) { exit 0 }

    Write-Host "Pr�buj� lokalnego klienta psql (localhost:5432) ..."
    $psqlCmd = "psql -h localhost -p 5432 -U $user -d $db -c \"$sql\""
    try {
        $process = Start-Process -FilePath psql -ArgumentList "-h","localhost","-p","5432","-U",$user,"-d",$db,"-c",$sql -NoNewWindow -Wait -PassThru -ErrorAction Stop
        if ($process.ExitCode -eq 0) { Write-Host "OK: wykonano lokalne psql"; exit 0 }
    } catch {
        Write-Host "Lokalny psql nie powiod� si�: $_" -ForegroundColor Red
    }

    Write-Host "Automatyczne metody nie zadzia�y. Uruchom r�cznie jedno z poni�szych poleceń:" -ForegroundColor Yellow
    Write-Host "docker exec -i $container psql -U $user -d $db -c \"$sql\""
    Write-Host "PS> psql -h localhost -p 5432 -U $user -d $db -c \"$sql\""
    exit 3
}

