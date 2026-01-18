package com.trendstream;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * TrendStream Main Application
 * 
 * Entry point for running TrendStream Flink jobs.
 * Supports running different jobs via command line arguments.
 * 
 * Usage:
 *   flink run trendstream-1.0.jar --job SessionAggregator
 *   flink run trendstream-1.0.jar --job FraudDetector
 *   flink run trendstream-1.0.jar --job RevenueCalculator
 */
public class TrendStreamApp {
    
    public static void main(String[] args) throws Exception {
        String jobName = "SessionAggregator"; // Default job
        
        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            if ("--job".equals(args[i]) && i + 1 < args.length) {
                jobName = args[i + 1];
            }
        }
        
        System.out.println("=".repeat(60));
        System.out.println("🚀 TrendStream - Real-Time E-Commerce Analytics");
        System.out.println("=".repeat(60));
        System.out.println("Starting job: " + jobName);
        System.out.println("=".repeat(60));
        
        switch (jobName) {
            case "SessionAggregator":
                com.trendstream.jobs.SessionAggregator.main(args);
                break;
            case "FraudDetector":
                com.trendstream.jobs.FraudDetector.main(args);
                break;
            case "RevenueCalculator":
                com.trendstream.jobs.RevenueCalculator.main(args);
                break;
            case "all":
                // Run all jobs in parallel (would need separate threads in production)
                System.out.println("Running all jobs is not supported in single-process mode.");
                System.out.println("Please submit each job separately.");
                break;
            default:
                System.err.println("Unknown job: " + jobName);
                System.err.println("Available jobs: SessionAggregator, FraudDetector, RevenueCalculator");
                System.exit(1);
        }
    }
}
