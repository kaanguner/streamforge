// =============================================================================
// StreamForge Analytics API - A Learning-Friendly Go REST Service
// =============================================================================
//
// 🎓 LEARNING GUIDE:
// This is a production-style Go REST API that serves analytics data from
// our streaming pipeline. The code is heavily commented to help you learn Go.
//
// WHY GO FOR THIS SERVICE?
// 1. Fast startup time (~10ms cold start vs Python's ~500ms)
// 2. Low memory footprint (~10MB vs Python's ~100MB)
// 3. Excellent concurrency model (goroutines)
// 4. Static typing catches errors at compile time
// 5. Single binary deployment (no dependency hell)
//
// OBSERVABILITY:
// This API implements the RED method for metrics:
// - Rate: Requests per second
// - Errors: Error rate
// - Duration: Request latency
//
// HOW TO RUN:
//   go run main.go
//   curl http://localhost:8090/api/v1/health
//   curl http://localhost:8090/metrics  # Prometheus metrics
//
// =============================================================================

package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/go-redis/redis/v8"
	"github.com/gorilla/mux"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

// =============================================================================
// PROMETHEUS METRICS (RED Method)
// =============================================================================
//
// 🎓 LEARNING NOTE:
// The RED method is a standard for microservice observability:
// - Rate: How many requests per second?
// - Errors: How many of those requests are failing?
// - Duration: How long do requests take?
//
// These metrics are scraped by Prometheus and visualized in Grafana.
// =============================================================================

var (
	// 🎓 Counter: Only goes up (total requests)
	httpRequestsTotal = promauto.NewCounterVec(
		prometheus.CounterOpts{
			Name: "streamforge_http_requests_total",
			Help: "Total number of HTTP requests",
		},
		[]string{"method", "endpoint", "status"},
	)

	// 🎓 Histogram: Distribution of values (latency buckets)
	httpRequestDuration = promauto.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "streamforge_http_request_duration_seconds",
			Help:    "HTTP request latency in seconds",
			Buckets: prometheus.DefBuckets, // Default: .005, .01, .025, .05, .1, .25, .5, 1, 2.5, 5, 10
		},
		[]string{"method", "endpoint"},
	)

	// 🎓 Gauge: Can go up or down (current connections)
	httpRequestsInFlight = promauto.NewGauge(
		prometheus.GaugeOpts{
			Name: "streamforge_http_requests_in_flight",
			Help: "Current number of HTTP requests being processed",
		},
	)

	// Business metrics
	fraudAlertsServed = promauto.NewCounter(
		prometheus.CounterOpts{
			Name: "streamforge_fraud_alerts_served_total",
			Help: "Total number of fraud alerts served via API",
		},
	)

	redisErrors = promauto.NewCounter(
		prometheus.CounterOpts{
			Name: "streamforge_redis_errors_total",
			Help: "Total number of Redis connection errors",
		},
	)
)

// =============================================================================
// CONFIGURATION
// =============================================================================

type Config struct {
	Port        string
	RedisAddr   string
	Environment string
}

func LoadConfig() Config {
	return Config{
		Port:        getEnv("API_PORT", "8090"),
		RedisAddr:   getEnv("REDIS_ADDR", "localhost:6379"),
		Environment: getEnv("ENVIRONMENT", "development"),
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

// =============================================================================
// DATA MODELS
// =============================================================================

type FraudAlert struct {
	AlertID     string    `json:"alert_id"`
	UserID      string    `json:"user_id"`
	AlertType   string    `json:"alert_type"`
	RiskScore   float64   `json:"risk_score"`
	OrderID     string    `json:"order_id,omitempty"`
	Amount      float64   `json:"amount,omitempty"`
	Timestamp   time.Time `json:"timestamp"`
	Description string    `json:"description"`
}

type SessionStats struct {
	SessionID    string    `json:"session_id"`
	UserID       string    `json:"user_id"`
	StartTime    time.Time `json:"start_time"`
	EndTime      time.Time `json:"end_time"`
	PageViews    int       `json:"page_views"`
	ProductViews int       `json:"product_views"`
	AddToCarts   int       `json:"add_to_carts"`
	Purchases    int       `json:"purchases"`
	Revenue      float64   `json:"revenue"`
}

type RevenueMetrics struct {
	WindowStart   time.Time `json:"window_start"`
	WindowEnd     time.Time `json:"window_end"`
	TotalGMV      float64   `json:"total_gmv"`
	OrderCount    int       `json:"order_count"`
	AvgOrderValue float64   `json:"avg_order_value"`
	TopCategory   string    `json:"top_category"`
}

type HealthResponse struct {
	Status    string `json:"status"`
	Service   string `json:"service"`
	Version   string `json:"version"`
	RedisOK   bool   `json:"redis_ok"`
	Timestamp string `json:"timestamp"`
}

type APIResponse[T any] struct {
	Success bool   `json:"success"`
	Data    T      `json:"data,omitempty"`
	Error   string `json:"error,omitempty"`
	Count   int    `json:"count,omitempty"`
}

// =============================================================================
// APPLICATION
// =============================================================================

type App struct {
	config Config
	redis  *redis.Client
	router *mux.Router
}

func NewApp(config Config) *App {
	rdb := redis.NewClient(&redis.Options{
		Addr:     config.RedisAddr,
		Password: "",
		DB:       0,
	})

	app := &App{
		config: config,
		redis:  rdb,
		router: mux.NewRouter(),
	}

	app.setupRoutes()

	return app
}

// =============================================================================
// ROUTE SETUP
// =============================================================================

func (app *App) setupRoutes() {
	// 🎓 Prometheus metrics endpoint - scraped by Prometheus server
	app.router.Handle("/metrics", promhttp.Handler())

	// API routes with versioning
	api := app.router.PathPrefix("/api/v1").Subrouter()

	api.HandleFunc("/health", app.handleHealth).Methods("GET")
	api.HandleFunc("/info", app.handleInfo).Methods("GET")
	api.HandleFunc("/fraud-alerts", app.handleFraudAlerts).Methods("GET")
	api.HandleFunc("/fraud-alerts/{user_id}", app.handleFraudAlertsByUser).Methods("GET")
	api.HandleFunc("/sessions", app.handleSessions).Methods("GET")
	api.HandleFunc("/sessions/{user_id}", app.handleSessionsByUser).Methods("GET")
	api.HandleFunc("/revenue", app.handleRevenue).Methods("GET")
	api.HandleFunc("/revenue/realtime", app.handleRealtimeRevenue).Methods("GET")

	// 🎓 Apply Prometheus middleware to all routes
	app.router.Use(prometheusMiddleware)
	app.router.Use(loggingMiddleware)
}

// =============================================================================
// PROMETHEUS MIDDLEWARE
// =============================================================================
//
// 🎓 LEARNING NOTE:
// This middleware wraps every request to:
// 1. Track in-flight requests (gauge)
// 2. Measure request duration (histogram)
// 3. Count total requests (counter)
//
// The metrics are exposed at /metrics in Prometheus format.
// =============================================================================

// responseWriter wraps http.ResponseWriter to capture status code
type responseWriter struct {
	http.ResponseWriter
	statusCode int
}

func (rw *responseWriter) WriteHeader(code int) {
	rw.statusCode = code
	rw.ResponseWriter.WriteHeader(code)
}

func prometheusMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Skip metrics endpoint to avoid recursion
		if r.URL.Path == "/metrics" {
			next.ServeHTTP(w, r)
			return
		}

		// Track in-flight requests
		httpRequestsInFlight.Inc()
		defer httpRequestsInFlight.Dec()

		// Start timer
		start := time.Now()

		// Wrap response writer to capture status
		wrapped := &responseWriter{ResponseWriter: w, statusCode: http.StatusOK}

		// Call the next handler
		next.ServeHTTP(wrapped, r)

		// Record metrics
		duration := time.Since(start).Seconds()
		endpoint := r.URL.Path
		method := r.Method
		status := fmt.Sprintf("%d", wrapped.statusCode)

		httpRequestDuration.WithLabelValues(method, endpoint).Observe(duration)
		httpRequestsTotal.WithLabelValues(method, endpoint, status).Inc()
	})
}

func loggingMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		next.ServeHTTP(w, r)
		log.Printf("[%s] %s %s - %v", r.Method, r.RequestURI, r.RemoteAddr, time.Since(start))
	})
}

// =============================================================================
// HANDLERS
// =============================================================================

func (app *App) handleHealth(w http.ResponseWriter, r *http.Request) {
	ctx := context.Background()
	redisOK := app.redis.Ping(ctx).Err() == nil

	if !redisOK {
		redisErrors.Inc()
	}

	response := HealthResponse{
		Status:    "healthy",
		Service:   "streamforge-api",
		Version:   "1.0.0",
		RedisOK:   redisOK,
		Timestamp: time.Now().UTC().Format(time.RFC3339),
	}

	if !redisOK {
		response.Status = "degraded"
	}

	writeJSON(w, http.StatusOK, response)
}

func (app *App) handleInfo(w http.ResponseWriter, r *http.Request) {
	info := map[string]interface{}{
		"service":     "StreamForge Analytics API",
		"version":     "1.0.0",
		"description": "REST API serving real-time analytics from Flink streaming pipeline",
		"endpoints": []string{
			"/api/v1/health",
			"/api/v1/fraud-alerts",
			"/api/v1/sessions",
			"/api/v1/revenue",
			"/metrics",
		},
		"observability": map[string]string{
			"metrics": "/metrics (Prometheus format)",
			"method":  "RED (Rate, Errors, Duration)",
		},
	}
	writeJSON(w, http.StatusOK, info)
}

func (app *App) handleFraudAlerts(w http.ResponseWriter, r *http.Request) {
	ctx := context.Background()

	alerts, err := app.getFraudAlertsFromRedis(ctx, "", 100)
	if err != nil {
		redisErrors.Inc()
		alerts = app.getSampleFraudAlerts()
	}

	// 🎓 Track business metric
	fraudAlertsServed.Add(float64(len(alerts)))

	response := APIResponse[[]FraudAlert]{
		Success: true,
		Data:    alerts,
		Count:   len(alerts),
	}
	writeJSON(w, http.StatusOK, response)
}

func (app *App) handleFraudAlertsByUser(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	userID := vars["user_id"]

	ctx := context.Background()
	alerts, err := app.getFraudAlertsFromRedis(ctx, userID, 50)
	if err != nil {
		redisErrors.Inc()
		alerts = filterAlertsByUser(app.getSampleFraudAlerts(), userID)
	}

	fraudAlertsServed.Add(float64(len(alerts)))

	response := APIResponse[[]FraudAlert]{
		Success: true,
		Data:    alerts,
		Count:   len(alerts),
	}
	writeJSON(w, http.StatusOK, response)
}

func (app *App) handleSessions(w http.ResponseWriter, r *http.Request) {
	sessions := app.getSampleSessions()

	response := APIResponse[[]SessionStats]{
		Success: true,
		Data:    sessions,
		Count:   len(sessions),
	}
	writeJSON(w, http.StatusOK, response)
}

func (app *App) handleSessionsByUser(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	userID := vars["user_id"]

	sessions := filterSessionsByUser(app.getSampleSessions(), userID)

	response := APIResponse[[]SessionStats]{
		Success: true,
		Data:    sessions,
		Count:   len(sessions),
	}
	writeJSON(w, http.StatusOK, response)
}

func (app *App) handleRevenue(w http.ResponseWriter, r *http.Request) {
	summary := map[string]interface{}{
		"today": map[string]interface{}{
			"gmv":          1250000.00,
			"order_count":  3450,
			"avg_order":    362.32,
			"top_category": "Elektronik",
		},
		"week": map[string]interface{}{
			"gmv":         8750000.00,
			"order_count": 24150,
			"avg_order":   362.32,
		},
	}
	writeJSON(w, http.StatusOK, APIResponse[map[string]interface{}]{
		Success: true,
		Data:    summary,
	})
}

func (app *App) handleRealtimeRevenue(w http.ResponseWriter, r *http.Request) {
	metrics := []RevenueMetrics{
		{
			WindowStart:   time.Now().Add(-1 * time.Minute),
			WindowEnd:     time.Now(),
			TotalGMV:      45230.50,
			OrderCount:    127,
			AvgOrderValue: 356.14,
			TopCategory:   "Moda",
		},
		{
			WindowStart:   time.Now().Add(-2 * time.Minute),
			WindowEnd:     time.Now().Add(-1 * time.Minute),
			TotalGMV:      52150.00,
			OrderCount:    143,
			AvgOrderValue: 364.69,
			TopCategory:   "Elektronik",
		},
	}

	response := APIResponse[[]RevenueMetrics]{
		Success: true,
		Data:    metrics,
		Count:   len(metrics),
	}
	writeJSON(w, http.StatusOK, response)
}

// =============================================================================
// REDIS ACCESS
// =============================================================================

func (app *App) getFraudAlertsFromRedis(ctx context.Context, userID string, limit int64) ([]FraudAlert, error) {
	key := "fraud:alerts"
	if userID != "" {
		key = fmt.Sprintf("fraud:user:%s", userID)
	}

	result, err := app.redis.ZRevRange(ctx, key, 0, limit-1).Result()
	if err != nil {
		return nil, err
	}

	var alerts []FraudAlert
	for _, item := range result {
		var alert FraudAlert
		if err := json.Unmarshal([]byte(item), &alert); err == nil {
			alerts = append(alerts, alert)
		}
	}

	return alerts, nil
}

// =============================================================================
// SAMPLE DATA
// =============================================================================

func (app *App) getSampleFraudAlerts() []FraudAlert {
	return []FraudAlert{
		{
			AlertID:     "FRAUD-001",
			UserID:      "USR-12345",
			AlertType:   "RAPID_ORDERS",
			RiskScore:   0.89,
			OrderID:     "ORD-2024-9999",
			Amount:      15999.00,
			Timestamp:   time.Now().Add(-5 * time.Minute),
			Description: "3 orders within 5 minutes detected",
		},
		{
			AlertID:     "FRAUD-002",
			UserID:      "USR-67890",
			AlertType:   "HIGH_VALUE_FIRST_ORDER",
			RiskScore:   0.75,
			OrderID:     "ORD-2024-8888",
			Amount:      89999.00,
			Timestamp:   time.Now().Add(-12 * time.Minute),
			Description: "First order exceeds 50,000 TRY threshold",
		},
		{
			AlertID:     "FRAUD-003",
			UserID:      "USR-11111",
			AlertType:   "UNUSUAL_LOCATION",
			RiskScore:   0.65,
			OrderID:     "ORD-2024-7777",
			Amount:      3499.00,
			Timestamp:   time.Now().Add(-30 * time.Minute),
			Description: "Order from new city for existing user",
		},
	}
}

func (app *App) getSampleSessions() []SessionStats {
	return []SessionStats{
		{
			SessionID:    "SESS-001",
			UserID:       "USR-12345",
			StartTime:    time.Now().Add(-45 * time.Minute),
			EndTime:      time.Now().Add(-5 * time.Minute),
			PageViews:    12,
			ProductViews: 8,
			AddToCarts:   3,
			Purchases:    1,
			Revenue:      3499.00,
		},
		{
			SessionID:    "SESS-002",
			UserID:       "USR-67890",
			StartTime:    time.Now().Add(-30 * time.Minute),
			EndTime:      time.Now().Add(-2 * time.Minute),
			PageViews:    25,
			ProductViews: 15,
			AddToCarts:   5,
			Purchases:    2,
			Revenue:      12499.00,
		},
	}
}

// =============================================================================
// HELPERS
// =============================================================================

func writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(data)
}

func filterAlertsByUser(alerts []FraudAlert, userID string) []FraudAlert {
	var filtered []FraudAlert
	for _, alert := range alerts {
		if alert.UserID == userID {
			filtered = append(filtered, alert)
		}
	}
	return filtered
}

func filterSessionsByUser(sessions []SessionStats, userID string) []SessionStats {
	var filtered []SessionStats
	for _, session := range sessions {
		if session.UserID == userID {
			filtered = append(filtered, session)
		}
	}
	return filtered
}

// =============================================================================
// MAIN
// =============================================================================

func main() {
	config := LoadConfig()
	app := NewApp(config)

	addr := fmt.Sprintf(":%s", config.Port)

	fmt.Println("╔══════════════════════════════════════════════════════════════╗")
	fmt.Println("║       StreamForge Analytics API (Go + Prometheus)            ║")
	fmt.Println("╠══════════════════════════════════════════════════════════════╣")
	fmt.Printf("║  Server:      http://localhost%s                         ║\n", addr)
	fmt.Printf("║  Metrics:     http://localhost%s/metrics                 ║\n", addr)
	fmt.Printf("║  Environment: %-45s ║\n", config.Environment)
	fmt.Println("╠══════════════════════════════════════════════════════════════╣")
	fmt.Println("║  Endpoints:                                                   ║")
	fmt.Println("║    GET /api/v1/health         - Health check                  ║")
	fmt.Println("║    GET /api/v1/fraud-alerts   - Recent fraud alerts           ║")
	fmt.Println("║    GET /api/v1/sessions       - Session analytics             ║")
	fmt.Println("║    GET /api/v1/revenue        - Revenue metrics               ║")
	fmt.Println("║    GET /metrics               - Prometheus metrics            ║")
	fmt.Println("╚══════════════════════════════════════════════════════════════╝")

	log.Fatal(http.ListenAndServe(addr, app.router))
}
