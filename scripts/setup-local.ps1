# TrendStream Local Setup Script
# PowerShell script for Windows

Write-Host "=" * 60 -ForegroundColor Cyan
Write-Host "🚀 TrendStream - Local Development Setup" -ForegroundColor Cyan
Write-Host "=" * 60 -ForegroundColor Cyan

# Check prerequisites
Write-Host "`n📋 Checking prerequisites..." -ForegroundColor Yellow

# Check Docker
try {
    $dockerVersion = docker --version
    Write-Host "✅ Docker installed: $dockerVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Docker not found. Please install Docker Desktop." -ForegroundColor Red
    exit 1
}

# Check Docker is running
try {
    docker info | Out-Null
    Write-Host "✅ Docker is running" -ForegroundColor Green
} catch {
    Write-Host "❌ Docker is not running. Please start Docker Desktop." -ForegroundColor Red
    exit 1
}

# Check Python
try {
    $pythonVersion = python --version
    Write-Host "✅ Python installed: $pythonVersion" -ForegroundColor Green
} catch {
    Write-Host "⚠️ Python not found. Event producer won't work." -ForegroundColor Yellow
}

# Check Java (optional, for Flink development)
try {
    $javaVersion = java -version 2>&1 | Select-Object -First 1
    Write-Host "✅ Java installed: $javaVersion" -ForegroundColor Green
} catch {
    Write-Host "⚠️ Java not found. Install JDK 11+ for Flink development." -ForegroundColor Yellow
}

# Check Maven (optional, for Flink development)
try {
    $mvnVersion = mvn --version | Select-Object -First 1
    Write-Host "✅ Maven installed: $mvnVersion" -ForegroundColor Green
} catch {
    Write-Host "⚠️ Maven not found. Install for building Flink jobs." -ForegroundColor Yellow
}

Write-Host "`n📦 Starting infrastructure..." -ForegroundColor Yellow

# Navigate to project root
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
Set-Location $projectRoot

# Pull Docker images
Write-Host "Pulling Docker images (this may take a few minutes)..." -ForegroundColor Cyan
docker-compose pull

# Start services
Write-Host "Starting services..." -ForegroundColor Cyan
docker-compose up -d

# Wait for services to be healthy
Write-Host "`n⏳ Waiting for services to start..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# Check Kafka health
$maxAttempts = 30
$attempt = 0
$kafkaReady = $false

while (-not $kafkaReady -and $attempt -lt $maxAttempts) {
    $attempt++
    Write-Host "Checking Kafka... (attempt $attempt/$maxAttempts)" -ForegroundColor Gray
    
    try {
        $topics = docker-compose exec -T kafka kafka-topics --bootstrap-server localhost:9092 --list 2>$null
        if ($LASTEXITCODE -eq 0) {
            $kafkaReady = $true
            Write-Host "✅ Kafka is ready!" -ForegroundColor Green
        }
    } catch {
        Start-Sleep -Seconds 3
    }
}

if (-not $kafkaReady) {
    Write-Host "❌ Kafka failed to start. Check logs with: docker-compose logs kafka" -ForegroundColor Red
    exit 1
}

# List topics
Write-Host "`n📋 Kafka Topics:" -ForegroundColor Yellow
docker-compose exec -T kafka kafka-topics --bootstrap-server localhost:9092 --list

# Service URLs
Write-Host "`n" + ("=" * 60) -ForegroundColor Cyan
Write-Host "✅ TrendStream is ready!" -ForegroundColor Green
Write-Host ("=" * 60) -ForegroundColor Cyan
Write-Host ""
Write-Host "📊 Service URLs:" -ForegroundColor Yellow
Write-Host "   Kafka UI:       http://localhost:8080" -ForegroundColor White
Write-Host "   Flink Dashboard: http://localhost:8082" -ForegroundColor White
Write-Host "   Schema Registry: http://localhost:8081" -ForegroundColor White
Write-Host ""
Write-Host "🚀 Next Steps:" -ForegroundColor Yellow
Write-Host "   1. Install Python dependencies:" -ForegroundColor White
Write-Host "      cd producer && pip install -r requirements.txt" -ForegroundColor Gray
Write-Host ""
Write-Host "   2. Run the event producer:" -ForegroundColor White
Write-Host "      python src/simulator.py --events 1000 --rate 100" -ForegroundColor Gray
Write-Host ""
Write-Host "   3. Build Flink jobs (requires Maven + JDK 11):" -ForegroundColor White
Write-Host "      cd flink-jobs && mvn clean package" -ForegroundColor Gray
Write-Host ""
Write-Host "   4. Submit a Flink job:" -ForegroundColor White
Write-Host "      docker cp flink-jobs/target/trendstream-flink-1.0-SNAPSHOT.jar trendstream-jobmanager:/opt/flink/lib/" -ForegroundColor Gray
Write-Host "      docker-compose exec flink-jobmanager flink run /opt/flink/lib/trendstream-flink-1.0-SNAPSHOT.jar" -ForegroundColor Gray
Write-Host ""
Write-Host ("=" * 60) -ForegroundColor Cyan
