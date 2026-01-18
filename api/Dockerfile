# =============================================================================
# StreamForge API - Multi-stage Docker Build
# =============================================================================
#
# 🎓 LEARNING NOTE:
# This Dockerfile uses a multi-stage build:
# 1. Builder stage: Compiles Go code (uses ~800MB image)
# 2. Runtime stage: Only the binary (uses ~10MB image!)
#
# This reduces container size by ~98% compared to single-stage builds.
# =============================================================================

# Stage 1: Builder
FROM golang:1.21-alpine AS builder

# Install git (needed for go mod download)
RUN apk add --no-cache git

# Set working directory
WORKDIR /app

# Copy go mod files first (for better caching)
COPY go.mod go.sum ./

# Download dependencies
# 🎓 This layer is cached if go.mod doesn't change
RUN go mod download

# Copy source code
COPY . .

# Build the binary
# CGO_ENABLED=0 creates a static binary (no libc dependency)
# -ldflags="-s -w" strips debug info for smaller binary
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o /api main.go

# =============================================================================
# Stage 2: Runtime (minimal image)
# =============================================================================

FROM alpine:3.19

# Add ca-certificates for HTTPS calls
RUN apk --no-cache add ca-certificates

# Create non-root user for security
RUN addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -D appuser

WORKDIR /app

# Copy binary from builder
COPY --from=builder /api .

# Use non-root user
USER appuser

# Expose port
EXPOSE 8090

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8090/api/v1/health || exit 1

# Run the binary
CMD ["./api"]
