<#
Simple PowerShell helper to:
 - get a JWT token from /api/auth/token?user=...
 - POST a sample transaction to /api/transactions with Authorization: Bearer <token>
 - GET /api/transactions and print result

Usage (PowerShell):
 1) Make sure your app is running (bootRun) on localhost:8080
 2) From project root run:
    .\scripts\add-sample-transaction.ps1

Parameters:
 -Host (default: http://localhost:8080)
 -UserName (default: dev)
 -TransactionId (default: TX-PS-001)
 -Amount (default: 150.00)
 -Currency (default: PLN)
 -Category (default: Jedzenie)
 -Description (default: "Obiad testowy")
 -Token (optional) - if provided, token retrieval step is skipped
#>

param(
    [string]$ApiHost = 'http://localhost:8080',
    [string]$UserName = 'dev',
    [string]$TransactionId = 'TX-PS-001',
    [decimal]$Amount = 150.00,
    [string]$Currency = 'PLN',
    [string]$Category = 'Jedzenie',
    [string]$Description = 'Obiad testowy',
    [switch]$MakeBigSpender = $false,
    [string]$Token = $null
)

function Write-ErrAndExit($msg) {
    Write-Host "ERROR: $msg" -ForegroundColor Red
    exit 1
}

# Ensure host is reachable
try {
    $health = Invoke-WebRequest -Uri "$ApiHost/actuator/health" -UseBasicParsing -Method Get -TimeoutSec 5 -ErrorAction Stop
} catch {
    Write-Host "Could not reach application at $ApiHost" -ForegroundColor Yellow
    Write-Host "Start the app (e.g. .\gradlew.bat bootRun) and ensure it's listening on port 8080." -ForegroundColor Yellow
    Write-ErrAndExit "Application not reachable"
}

# Get token if not provided
if (-not $Token) {
    Write-Host "Requesting JWT for user '$UserName' from $ApiHost/api/auth/token..."
    try {
        $resp = Invoke-RestMethod -Uri "$ApiHost/api/auth/token?user=$UserName" -Method Get -ErrorAction Stop
        if ($null -eq $resp.token) { Write-ErrAndExit "Response did not contain token field." }
        $Token = $resp.token
    } catch {
        Write-ErrAndExit "Failed to get token: $($_.Exception.Message)"
    }
}

Write-Host "Using token (truncated): $($Token.Substring(0,20))..." -ForegroundColor Green

# If MakeBigSpender flag is set, ensure amount is negative and adjust id
if ($MakeBigSpender) {
    # If user provided Amount > 0, use its absolute value; otherwise default to 200
    if ($Amount -ne 0) {
        $Amount = -[decimal][math]::Abs([double]$Amount)
    } else {
        $Amount = -200.00
    }
    # Append suffix to transaction id for clarity
    if ($TransactionId -notlike '*-BIG') { $TransactionId = "$TransactionId-BIG" }
    Write-Host "MakeBigSpender enabled: sending amount $Amount with id $TransactionId" -ForegroundColor Yellow
}

# Build transaction body
$body = @{
    id = $TransactionId
    date = (Get-Date).ToString('yyyy-MM-dd')
    amount = [decimal]$Amount
    currency = $Currency
    category = $Category
    description = $Description
} | ConvertTo-Json

Write-Host "Posting transaction to $ApiHost/api/transactions ..."
try {
    $postResp = Invoke-RestMethod -Uri "$ApiHost/api/transactions" -Method Post -Headers @{ Authorization = "Bearer $Token" } -ContentType 'application/json' -Body $body -ErrorAction Stop
    Write-Host "Transaction posted successfully. Response object:" -ForegroundColor Green
    $postResp | ConvertTo-Json -Depth 5 | Write-Host
} catch {
    # Try to extract response body for better debugging
    $status = $null
    $bodyText = $null
    try {
        if ($_.Exception.Response -ne $null) {
            $status = $_.Exception.Response.StatusCode.Value__ 2>$null
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $bodyText = $reader.ReadToEnd()
            $reader.Close()
        }
    } catch {
        # ignore
    }
    if ($bodyText) {
        Write-Host "Server returned status: $status" -ForegroundColor Red
        Write-Host "Response body:" -ForegroundColor Red
        Write-Host $bodyText
        Write-ErrAndExit "Failed to POST transaction: $status - $($_.Exception.Message)"
    } else {
        Write-ErrAndExit "Failed to POST transaction: $($_.Exception.Response.StatusCode.Value__ 2>$null) - $($_.Exception.Message)"
    }
}

# Fetch list
Write-Host "Fetching transactions list..."
try {
    $list = Invoke-RestMethod -Uri "$ApiHost/api/transactions" -Method Get -Headers @{ Authorization = "Bearer $Token" } -ErrorAction Stop
    Write-Host "Transactions:" -ForegroundColor Cyan
    $list | ConvertTo-Json -Depth 5 | Write-Host
} catch {
    Write-ErrAndExit "Failed to GET transactions: $($_.Exception.Message)"
}

Write-Host "Done." -ForegroundColor Green

