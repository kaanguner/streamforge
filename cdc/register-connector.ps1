# TrendStream CDC: Register Debezium Connector
# Run this after docker-compose up to enable CDC

Write-Host "=" * 60 -ForegroundColor Cyan
Write-Host "🔄 TrendStream - Registering Debezium CDC Connector" -ForegroundColor Cyan
Write-Host "=" * 60 -ForegroundColor Cyan

# Wait for Kafka Connect to be ready
Write-Host "`n⏳ Waiting for Kafka Connect to be ready..." -ForegroundColor Yellow
$maxAttempts = 30
$attempt = 0
$ready = $false

while (-not $ready -and $attempt -lt $maxAttempts) {
    $attempt++
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8083/connectors" -Method Get -ErrorAction Stop
        $ready = $true
        Write-Host "✅ Kafka Connect is ready!" -ForegroundColor Green
    }
    catch {
        Write-Host "   Attempt $attempt/$maxAttempts - Waiting..." -ForegroundColor Gray
        Start-Sleep -Seconds 5
    }
}

if (-not $ready) {
    Write-Host "❌ Kafka Connect failed to start. Check logs:" -ForegroundColor Red
    Write-Host "   docker logs trendstream-kafka-connect" -ForegroundColor Gray
    exit 1
}

# Register the PostgreSQL connector
Write-Host "`n📋 Registering PostgreSQL CDC connector..." -ForegroundColor Yellow

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$connectorConfig = Get-Content "$scriptDir\postgres-connector.json" -Raw

try {
    $result = Invoke-RestMethod -Uri "http://localhost:8083/connectors" `
        -Method Post `
        -ContentType "application/json" `
        -Body $connectorConfig `
        -ErrorAction Stop
    
    Write-Host "✅ Connector '$($result.name)' registered successfully!" -ForegroundColor Green
}
catch {
    if ($_.Exception.Response.StatusCode -eq 409) {
        Write-Host "⚠️ Connector already exists. Updating..." -ForegroundColor Yellow
        $result = Invoke-RestMethod -Uri "http://localhost:8083/connectors/trendstream-postgres-connector/config" `
            -Method Put `
            -ContentType "application/json" `
            -Body ((Get-Content "$scriptDir\postgres-connector.json" | ConvertFrom-Json).config | ConvertTo-Json) `
            -ErrorAction Stop
        Write-Host "✅ Connector updated!" -ForegroundColor Green
    }
    else {
        Write-Host "❌ Failed to register connector: $_" -ForegroundColor Red
        exit 1
    }
}

# Verify connector status
Write-Host "`n📊 Checking connector status..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

$status = Invoke-RestMethod -Uri "http://localhost:8083/connectors/trendstream-postgres-connector/status"
Write-Host "   Connector state: $($status.connector.state)" -ForegroundColor White
Write-Host "   Task 0 state: $($status.tasks[0].state)" -ForegroundColor White

# List CDC topics
Write-Host "`n📋 CDC Topics created:" -ForegroundColor Yellow
$topics = docker exec trendstream-kafka kafka-topics --bootstrap-server localhost:9092 --list 2>$null | Select-String "cdc\."
$topics | ForEach-Object { Write-Host "   $_" -ForegroundColor White }

Write-Host "`n" + ("=" * 60) -ForegroundColor Cyan
Write-Host "✅ Debezium CDC Pipeline Ready!" -ForegroundColor Green
Write-Host ("=" * 60) -ForegroundColor Cyan
Write-Host ""
Write-Host "📊 Monitor CDC events:" -ForegroundColor Yellow
Write-Host "   • Kafka UI: http://localhost:8080 (look for cdc.* topics)" -ForegroundColor White
Write-Host "   • Connect UI: http://localhost:8083/connectors" -ForegroundColor White
Write-Host ""
Write-Host "🔄 Trigger CDC events by updating PostgreSQL:" -ForegroundColor Yellow
Write-Host '   docker exec -it trendstream-postgres psql -U trendstream -d ecommerce -c "UPDATE products SET price = price + 1 WHERE product_id = 1;"' -ForegroundColor Gray
Write-Host ""
