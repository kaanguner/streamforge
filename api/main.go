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
// HOW TO RUN:
//   go run main.go
//   curl http://localhost:8090/api/health
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

	"github.com/go-redis/redis/v8" // Redis client
	"github.com/gorilla/mux"       // HTTP router (like Express.js or Flask)
)

// =============================================================================
// CONFIGURATION
// =============================================================================
//
// 🎓 LEARNING NOTE:
// In Go, we use structs to define data structures. This is similar to
// Python's dataclasses or TypeScript interfaces.
//
// The `json:"field_name"` tags tell Go how to serialize/deserialize JSON.
// =============================================================================

// Config holds all application configuration
type Config struct {
	Port        string
	RedisAddr   string
	Environment string
}

// LoadConfig reads configuration from environment variables
// 🎓 Go idiom: functions that start with capital letters are "exported" (public)
func LoadConfig() Config {
	return Config{
		Port:        getEnv("API_PORT", "8090"),
		RedisAddr:   getEnv("REDIS_ADDR", "localhost:6379"),
		Environment: getEnv("ENVIRONMENT", "development"),
	}
}

// getEnv returns environment variable or default value
// 🎓 Go idiom: lowercase function = private to this package
func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

// =============================================================================
// DATA MODELS
// =============================================================================
//
// 🎓 LEARNING NOTE:
// These structs match the data produced by our Flink jobs.
// The JSON tags define how they serialize to/from JSON.
// =============================================================================

// FraudAlert represents a fraud detection alert from Flink
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

// SessionStats represents aggregated session data
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

// RevenueMetrics represents real-time revenue data
type RevenueMetrics struct {
	WindowStart   time.Time `json:"window_start"`
	WindowEnd     time.Time `json:"window_end"`
	TotalGMV      float64   `json:"total_gmv"`
	OrderCount    int       `json:"order_count"`
	AvgOrderValue float64   `json:"avg_order_value"`
	TopCategory   string    `json:"top_category"`
}

// HealthResponse for the health check endpoint
type HealthResponse struct {
	Status    string `json:"status"`
	Service   string `json:"service"`
	Version   string `json:"version"`
	RedisOK   bool   `json:"redis_ok"`
	Timestamp string `json:"timestamp"`
}

// APIResponse wraps all API responses
// 🎓 Go generics (Go 1.18+): T can be any type
type APIResponse[T any] struct {
	Success bool   `json:"success"`
	Data    T      `json:"data,omitempty"`
	Error   string `json:"error,omitempty"`
	Count   int    `json:"count,omitempty"`
}

// =============================================================================
// APPLICATION STRUCT
// =============================================================================
//
// 🎓 LEARNING NOTE:
// In Go, we don't have classes, but we use structs with methods.
// This is similar to Python's class with self, but more explicit.
// =============================================================================

// App holds the application state and dependencies
type App struct {
	config Config
	redis  *redis.Client
	router *mux.Router
}

// NewApp creates a new application instance
// 🎓 Go idiom: "constructor" functions are named New<Type>
func NewApp(config Config) *App {
	// Create Redis client
	// 🎓 The redis.Options struct uses named fields (similar to Python kwargs)
	rdb := redis.NewClient(&redis.Options{
		Addr:     config.RedisAddr,
		Password: "", // No password for local dev
		DB:       0,  // Default DB
	})

	app := &App{
		config: config,
		redis:  rdb,
		router: mux.NewRouter(),
	}

	// Register routes
	app.setupRoutes()

	return app
}

// =============================================================================
// ROUTE SETUP
// =============================================================================

func (app *App) setupRoutes() {
	// 🎓 Method syntax: (app *App) means this function belongs to App
	// It's like Python's 'self' but explicitly declared

	// API versioning via path prefix
	api := app.router.PathPrefix("/api/v1").Subrouter()

	// Health & info endpoints
	api.HandleFunc("/health", app.handleHealth).Methods("GET")
	api.HandleFunc("/info", app.handleInfo).Methods("GET")

	// Analytics endpoints (the "Serving Layer")
	api.HandleFunc("/fraud-alerts", app.handleFraudAlerts).Methods("GET")
	api.HandleFunc("/fraud-alerts/{user_id}", app.handleFraudAlertsByUser).Methods("GET")
	api.HandleFunc("/sessions", app.handleSessions).Methods("GET")
	api.HandleFunc("/sessions/{user_id}", app.handleSessionsByUser).Methods("GET")
	api.HandleFunc("/revenue", app.handleRevenue).Methods("GET")
	api.HandleFunc("/revenue/realtime", app.handleRealtimeRevenue).Methods("GET")

	// Middleware for logging
	app.router.Use(loggingMiddleware)
}

// =============================================================================
// MIDDLEWARE
// =============================================================================
//
// 🎓 LEARNING NOTE:
// Middleware in Go wraps handlers to add cross-cutting concerns.
// This pattern is used in almost every Go web framework.
// =============================================================================

func loggingMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()

		// Call the next handler
		next.ServeHTTP(w, r)

		// Log after request completes
		log.Printf(
			"[%s] %s %s - %v",
			r.Method,
			r.RequestURI,
			r.RemoteAddr,
			time.Since(start),
		)
	})
}

// =============================================================================
// HANDLER FUNCTIONS
// =============================================================================
//
// 🎓 LEARNING NOTE:
// Handlers in Go take (w http.ResponseWriter, r *http.Request)
// - w: Used to write the response (like Python's return jsonify(...))
// - r: Contains the request data (headers, body, params)
// =============================================================================

// handleHealth returns API health status
func (app *App) handleHealth(w http.ResponseWriter, r *http.Request) {
	// Check Redis connectivity
	ctx := context.Background()
	redisOK := app.redis.Ping(ctx).Err() == nil

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

// handleInfo returns API information
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
		},
		"data_sources": map[string]string{
			"fraud_alerts": "Redis (from Flink FraudDetector)",
			"sessions":     "Redis (from Flink SessionAggregator)",
			"revenue":      "Redis (from Flink RevenueCalculator)",
		},
	}
	writeJSON(w, http.StatusOK, info)
}

// handleFraudAlerts returns recent fraud alerts
func (app *App) handleFraudAlerts(w http.ResponseWriter, r *http.Request) {
	ctx := context.Background()

	// 🎓 Try to get from Redis first
	// In production, Flink writes alerts to Redis for fast access
	alerts, err := app.getFraudAlertsFromRedis(ctx, "", 100)
	if err != nil {
		// Fallback to sample data for demo
		alerts = app.getSampleFraudAlerts()
	}

	response := APIResponse[[]FraudAlert]{
		Success: true,
		Data:    alerts,
		Count:   len(alerts),
	}
	writeJSON(w, http.StatusOK, response)
}

// handleFraudAlertsByUser returns alerts for a specific user
func (app *App) handleFraudAlertsByUser(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r) // 🎓 Extract path parameters
	userID := vars["user_id"]

	ctx := context.Background()
	alerts, err := app.getFraudAlertsFromRedis(ctx, userID, 50)
	if err != nil {
		alerts = filterAlertsByUser(app.getSampleFraudAlerts(), userID)
	}

	response := APIResponse[[]FraudAlert]{
		Success: true,
		Data:    alerts,
		Count:   len(alerts),
	}
	writeJSON(w, http.StatusOK, response)
}

// handleSessions returns aggregated session data
func (app *App) handleSessions(w http.ResponseWriter, r *http.Request) {
	sessions := app.getSampleSessions()

	response := APIResponse[[]SessionStats]{
		Success: true,
		Data:    sessions,
		Count:   len(sessions),
	}
	writeJSON(w, http.StatusOK, response)
}

// handleSessionsByUser returns sessions for a specific user
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

// handleRevenue returns revenue metrics summary
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

// handleRealtimeRevenue returns streaming revenue metrics
func (app *App) handleRealtimeRevenue(w http.ResponseWriter, r *http.Request) {
	// 🎓 This would read from Redis where Flink writes every minute
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
// REDIS DATA ACCESS
// =============================================================================

func (app *App) getFraudAlertsFromRedis(ctx context.Context, userID string, limit int64) ([]FraudAlert, error) {
	// 🎓 In production, Flink writes alerts to a Redis sorted set
	// Key pattern: "fraud:alerts" or "fraud:user:{user_id}"

	key := "fraud:alerts"
	if userID != "" {
		key = fmt.Sprintf("fraud:user:%s", userID)
	}

	// Get latest alerts from sorted set
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
// SAMPLE DATA (for demo when Redis is empty)
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
// HELPER FUNCTIONS
// =============================================================================

// writeJSON sends a JSON response
// 🎓 This is a common pattern in Go - extract repetitive code into helpers
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
// MAIN ENTRY POINT
// =============================================================================

func main() {
	// Load configuration
	config := LoadConfig()

	// Create application
	app := NewApp(config)

	// Start server
	addr := fmt.Sprintf(":%s", config.Port)

	fmt.Println("╔══════════════════════════════════════════════════════════════╗")
	fmt.Println("║           StreamForge Analytics API (Go)                      ║")
	fmt.Println("╠══════════════════════════════════════════════════════════════╣")
	fmt.Printf("║  Server:      http://localhost%s                         ║\n", addr)
	fmt.Printf("║  Environment: %-45s ║\n", config.Environment)
	fmt.Printf("║  Redis:       %-45s ║\n", config.RedisAddr)
	fmt.Println("╠══════════════════════════════════════════════════════════════╣")
	fmt.Println("║  Endpoints:                                                   ║")
	fmt.Println("║    GET /api/v1/health         - Health check                  ║")
	fmt.Println("║    GET /api/v1/fraud-alerts   - Recent fraud alerts           ║")
	fmt.Println("║    GET /api/v1/sessions       - Session analytics             ║")
	fmt.Println("║    GET /api/v1/revenue        - Revenue metrics               ║")
	fmt.Println("╚══════════════════════════════════════════════════════════════╝")

	// 🎓 ListenAndServe blocks and runs the server
	// In production, you'd add graceful shutdown handling
	log.Fatal(http.ListenAndServe(addr, app.router))
}
