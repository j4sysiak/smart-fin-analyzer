<#
Simple helper script: uruchamia integracyjne testy z profilem 'tc' i Flyway włączonym,
i następnie wyświetla zawartość tabeli flyway_schema_history z kontenera testowego.

Użycie:
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-integration-tests-with-flyway.ps1

#>
param(
    [string]$Gradle = ".\gradlew.bat",
    [string]$Profile = "tc",
    [string]$TestFilter = ""
)

Write-Host "Uruchamiam testy integracyjne z profilem: $Profile (Flyway włączony)" -ForegroundColor Cyan

# $TestFilter można pozostawić puste lub przekazać np. '--tests "*CqrsSpec*"'
& $Gradle "-Dspring.profiles.active=$Profile" "-Denable.flyway=true" clean test $TestFilter

Write-Host "Szukam kontenera smartfin-test-pg..." -ForegroundColor Cyan
$container = (docker ps --filter "name=smartfin-test-pg" --format "{{.Names}}")
if (-not $container) {
    Write-Host "Kontener 'smartfin-test-pg' nie znaleziony. Sprawdź logi Gradle — testy mogły wystartować inny ephemeryczny kontener." -ForegroundColor Yellow
    exit 0
}

Write-Host "Znaleziono kontener: $container" -ForegroundColor Green
Write-Host "Wyświetlam flyway_schema_history..." -ForegroundColor Cyan
docker exec -it $container psql -U test -d testdb -c "SELECT version, description, installed_rank, success, installed_on FROM flyway_schema_history ORDER BY installed_rank;"

Write-Host "Gotowe." -ForegroundColor Green

