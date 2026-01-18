# TrendStream GCP Deployment Script
# PowerShell script for deploying to Google Cloud Platform

param(
    [string]$ProjectId = "new-project-id",
    [string]$Region = "europe-west1",
    [string]$Environment = "dev"
)

Write-Host "=" * 60 -ForegroundColor Cyan
Write-Host "🚀 TrendStream - GCP Deployment" -ForegroundColor Cyan
Write-Host "=" * 60 -ForegroundColor Cyan
Write-Host "Project:     $ProjectId" -ForegroundColor White
Write-Host "Region:      $Region" -ForegroundColor White
Write-Host "Environment: $Environment" -ForegroundColor White
Write-Host "=" * 60 -ForegroundColor Cyan

# Check prerequisites
Write-Host "`n📋 Checking prerequisites..." -ForegroundColor Yellow

# Check gcloud
try {
    $gcloudVersion = gcloud --version | Select-Object -First 1
    Write-Host "✅ gcloud CLI installed" -ForegroundColor Green
} catch {
    Write-Host "❌ gcloud CLI not found. Install from: https://cloud.google.com/sdk/docs/install" -ForegroundColor Red
    exit 1
}

# Check Terraform
try {
    $tfVersion = terraform --version | Select-Object -First 1
    Write-Host "✅ Terraform installed: $tfVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Terraform not found. Install from: https://www.terraform.io/downloads" -ForegroundColor Red
    exit 1
}

# Check kubectl
try {
    $kubectlVersion = kubectl version --client --short 2>$null
    Write-Host "✅ kubectl installed" -ForegroundColor Green
} catch {
    Write-Host "⚠️ kubectl not found. Install for GKE access." -ForegroundColor Yellow
}

# Authenticate with GCP
Write-Host "`n🔐 Authenticating with GCP..." -ForegroundColor Yellow
gcloud auth application-default login

# Set project
Write-Host "`n📁 Setting GCP project to $ProjectId..." -ForegroundColor Yellow
gcloud config set project $ProjectId

# Navigate to Terraform directory
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$terraformDir = Join-Path (Split-Path -Parent $scriptDir) "infrastructure\terraform"
Set-Location $terraformDir

Write-Host "`n🏗️ Deploying infrastructure..." -ForegroundColor Yellow

# Initialize Terraform
Write-Host "Initializing Terraform..." -ForegroundColor Cyan
terraform init

# Plan
Write-Host "`nCreating Terraform plan..." -ForegroundColor Cyan
terraform plan `
    -var="project_id=$ProjectId" `
    -var="region=$Region" `
    -var="environment=$Environment" `
    -out=tfplan

# Ask for confirmation
$confirm = Read-Host "`n⚠️ Review the plan above. Apply changes? (yes/no)"
if ($confirm -ne "yes") {
    Write-Host "Deployment cancelled." -ForegroundColor Yellow
    exit 0
}

# Apply
Write-Host "`nApplying Terraform plan..." -ForegroundColor Cyan
terraform apply tfplan

# Get outputs
$gkeCluster = terraform output -raw gke_cluster_name
$bigqueryDataset = terraform output -raw bigquery_dataset

# Configure kubectl
Write-Host "`n🔧 Configuring kubectl..." -ForegroundColor Yellow
gcloud container clusters get-credentials $gkeCluster --region $Region

# Verify connection
Write-Host "`n📊 Cluster nodes:" -ForegroundColor Yellow
kubectl get nodes

Write-Host "`n" + ("=" * 60) -ForegroundColor Cyan
Write-Host "✅ Deployment complete!" -ForegroundColor Green
Write-Host ("=" * 60) -ForegroundColor Cyan
Write-Host ""
Write-Host "📊 Resources created:" -ForegroundColor Yellow
Write-Host "   GKE Cluster:      $gkeCluster" -ForegroundColor White
Write-Host "   BigQuery Dataset: $bigqueryDataset" -ForegroundColor White
Write-Host ""
Write-Host "🚀 Next Steps:" -ForegroundColor Yellow
Write-Host "   1. Build Flink jobs: cd ../flink-jobs && mvn clean package" -ForegroundColor Gray
Write-Host "   2. Deploy Flink to GKE (see docs/DEPLOYMENT.md)" -ForegroundColor Gray
Write-Host "   3. Create Looker Studio dashboard connected to BigQuery" -ForegroundColor Gray
Write-Host ""
Write-Host ("=" * 60) -ForegroundColor Cyan
