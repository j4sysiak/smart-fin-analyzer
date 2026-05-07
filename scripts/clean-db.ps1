param(
    [ValidateSet('local-pg', 'tc')]
    [string]$Mode = 'local-pg',
    [switch]$Force
)

function Invoke-DockerPsql {
    param(
        [string]$Container,
        [string]$User,
        [string]$Database,
        [string]$Sql
    )

    Write-Host "Trying docker exec on container: $Container"
    $output = & docker exec -i $Container psql -U $User -d $Database -c $Sql 2>&1 | Out-String
    if ($LASTEXITCODE -eq 0) {
        Write-Host "OK: cleanup executed inside container $Container"
        return $true
    }

    Write-Host "docker exec failed: $output" -ForegroundColor Red
    return $false
}

function Invoke-LocalPsql {
    param(
        [string]$User,
        [string]$Database,
        [string]$Sql
    )

    Write-Host "Trying local psql on localhost:5432 ..."
    try {
        $process = Start-Process -FilePath psql -ArgumentList '-h', 'localhost', '-p', '5432', '-U', $User, '-d', $Database, '-c', $Sql -NoNewWindow -Wait -PassThru -ErrorAction Stop
        if ($process.ExitCode -eq 0) {
            Write-Host 'OK: cleanup executed by local psql'
            return $true
        }

        Write-Host "local psql exited with code $($process.ExitCode)" -ForegroundColor Red
        return $false
    } catch {
        Write-Host "local psql failed: $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

$sql = 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;'

if (-not $Force) {
    Write-Host 'WARNING: this will remove ALL data in the test database!' -ForegroundColor Yellow
    $answer = Read-Host 'Continue? (y/N)'
    if ($answer -notin @('y', 'Y')) {
        Write-Host 'Aborted by user.'
        exit 1
    }
}

if ($Mode -eq 'tc') {
    $container = 'smartfin-test-pg'
    $user = 'test'
    $db = 'testdb'

    if (Invoke-DockerPsql -Container $container -User $user -Database $db -Sql $sql) {
        exit 0
    }

    Write-Host 'Cleanup via docker exec failed. Run this manually:' -ForegroundColor Yellow
    Write-Host ('docker exec -i {0} psql -U {1} -d {2} -c {3}' -f $container, $user, $db, $sql)
    exit 2
}

$container = 'smartfin-postgres'
$user = 'finuser'
$db = 'smartfin_test'

if (Invoke-DockerPsql -Container $container -User $user -Database $db -Sql $sql) {
    exit 0
}

if (Invoke-LocalPsql -User $user -Database $db -Sql $sql) {
    exit 0
}

Write-Host 'Automatic cleanup failed. Run one of these commands manually:' -ForegroundColor Yellow
Write-Host ('docker exec -i {0} psql -U {1} -d {2} -c {3}' -f $container, $user, $db, $sql)
Write-Host ('psql -h localhost -p 5432 -U {0} -d {1} -c {2}' -f $user, $db, $sql)
exit 3

