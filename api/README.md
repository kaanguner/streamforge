# StreamForge Analytics API (Go)

A high-performance REST API serving real-time analytics from the Flink streaming pipeline.

## 🎓 Why Go?

This API is written in Go (Golang) because:

1. **Performance**: ~10ms cold start vs Python's ~500ms
2. **Low Memory**: ~10MB footprint vs Python's ~100MB
3. **Concurrency**: Built-in goroutines for handling thousands of concurrent requests
4. **Single Binary**: No dependency management, just one executable
5. **Type Safety**: Compile-time error detection

## 🚀 Quick Start

### Prerequisites
- Go 1.21+ ([download](https://go.dev/dl/))
- Redis running (from docker-compose)

### Run Locally

```bash
# Install dependencies
go mod download

# Run the API
go run main.go

# Test the endpoints
curl http://localhost:8090/api/v1/health
curl http://localhost:8090/api/v1/fraud-alerts
curl http://localhost:8090/api/v1/sessions
curl http://localhost:8090/api/v1/revenue
```

### Run with Docker

```bash
# Build the image
docker build -t streamforge-api .

# Run the container
docker run -p 8090:8090 \
  -e REDIS_ADDR=host.docker.internal:6379 \
  streamforge-api
```

## 📡 API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/health` | GET | Health check with Redis status |
| `/api/v1/info` | GET | API information and available endpoints |
| `/api/v1/fraud-alerts` | GET | Recent fraud detection alerts |
| `/api/v1/fraud-alerts/{user_id}` | GET | Alerts for specific user |
| `/api/v1/sessions` | GET | Aggregated session statistics |
| `/api/v1/sessions/{user_id}` | GET | Sessions for specific user |
| `/api/v1/revenue` | GET | Revenue summary (today/week) |
| `/api/v1/revenue/realtime` | GET | Real-time revenue from Flink |

## 📊 Response Format

All endpoints return consistent JSON:

```json
{
  "success": true,
  "data": [...],
  "count": 3
}
```

## 🔧 Configuration

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| `API_PORT` | `8090` | Port to run the API |
| `REDIS_ADDR` | `localhost:6379` | Redis connection address |
| `ENVIRONMENT` | `development` | Environment name |

## 📁 Code Structure

```
api/
├── main.go          # Application entry point + all handlers
├── go.mod           # Go module dependencies
├── go.sum           # Dependency checksums
├── Dockerfile       # Multi-stage Docker build
└── README.md        # This file
```

## 🎓 Learning Go

The `main.go` file is heavily commented to help you learn Go concepts:

- **Structs & JSON tags**: How Go handles data serialization
- **Methods on structs**: Go's approach to OOP
- **HTTP handlers**: The standard library pattern
- **Middleware**: Request/response wrapping
- **Environment configuration**: The 12-factor app pattern
- **Error handling**: Go's explicit error handling

## 🔗 Data Flow

```
Flink Jobs → Redis (Feature Store) → Go API → Client
    │
    ├── FraudDetector    → fraud:alerts
    ├── SessionAggregator → sessions:user:{id}
    └── RevenueCalculator → revenue:realtime
```

## 📈 Production Considerations

For production deployment:

1. **Add authentication** (JWT/OAuth2)
2. **Add rate limiting** 
3. **Add metrics** (Prometheus)
4. **Add graceful shutdown**
5. **Deploy to GKE** with the included Dockerfile
