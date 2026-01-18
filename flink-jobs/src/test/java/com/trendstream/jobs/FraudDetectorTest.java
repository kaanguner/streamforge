package com.trendstream.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.trendstream.model.FraudAlert;
import com.trendstream.model.OrderEvent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for the FraudDetector Flink job. */
public class FraudDetectorTest {

  private OrderEvent normalOrder;
  private OrderEvent highValueOrder;
  private OrderEvent rapidOrder1;
  private OrderEvent rapidOrder2;
  private OrderEvent rapidOrder3;

  @BeforeEach
  void setUp() {
    normalOrder = new OrderEvent();
    normalOrder.setOrderId("ORD-001");
    normalOrder.setUserId("USR-NORMAL");
    normalOrder.setTotalAmount(500.0);
    normalOrder.setEventType("order_placed");
    normalOrder.setTimestamp(Instant.now().toEpochMilli());
    normalOrder.setCity("Istanbul");

    highValueOrder = new OrderEvent();
    highValueOrder.setOrderId("ORD-002");
    highValueOrder.setUserId("USR-NEW");
    highValueOrder.setTotalAmount(75000.0);
    highValueOrder.setEventType("order_placed");
    highValueOrder.setTimestamp(Instant.now().toEpochMilli());
    highValueOrder.setCity("Ankara");

    long baseTime = Instant.now().toEpochMilli();
    rapidOrder1 = createOrderEvent("ORD-R1", "USR-FRAUD", 5000.0, baseTime);
    rapidOrder2 = createOrderEvent("ORD-R2", "USR-FRAUD", 5000.0, baseTime + 60000);
    rapidOrder3 = createOrderEvent("ORD-R3", "USR-FRAUD", 5000.0, baseTime + 120000);
  }

  private OrderEvent createOrderEvent(
      String orderId, String userId, double amount, long timestamp) {
    OrderEvent order = new OrderEvent();
    order.setOrderId(orderId);
    order.setUserId(userId);
    order.setTotalAmount(amount);
    order.setEventType("order_placed");
    order.setTimestamp(timestamp);
    order.setCity("Istanbul");
    return order;
  }

  @Test
  @DisplayName("Should detect high-value first order for new user")
  void testHighValueFirstOrderDetection() {
    FraudDetector.HighValueFirstOrderDetector detector =
        new FraudDetector.HighValueFirstOrderDetector();

    List<FraudAlert> alerts = detector.processOrder(highValueOrder, null);

    assertThat(alerts).hasSize(1);
    assertThat(alerts.get(0).getAlertType()).isEqualTo("HIGH_VALUE_FIRST_ORDER");
    assertThat(alerts.get(0).getRiskScore()).isGreaterThanOrEqualTo(0.7);
    assertThat(alerts.get(0).getUserId()).isEqualTo("USR-NEW");
  }

  @Test
  @DisplayName("Should NOT alert on high-value order from existing user")
  void testHighValueOrderExistingUser() {
    FraudDetector.HighValueFirstOrderDetector detector =
        new FraudDetector.HighValueFirstOrderDetector();

    detector.processOrder(normalOrder, null);

    OrderEvent secondOrder =
        createOrderEvent("ORD-003", "USR-NORMAL", 75000.0, Instant.now().toEpochMilli());
    List<FraudAlert> alerts = detector.processOrder(secondOrder, true);

    assertThat(alerts).isEmpty();
  }

  @Test
  @DisplayName("Should NOT alert on normal value first order")
  void testNormalFirstOrder() {
    FraudDetector.HighValueFirstOrderDetector detector =
        new FraudDetector.HighValueFirstOrderDetector();

    List<FraudAlert> alerts = detector.processOrder(normalOrder, null);

    assertThat(alerts).isEmpty();
  }

  @Test
  @DisplayName("Should calculate correct risk score based on amount")
  void testRiskScoreCalculation() {
    double[] amounts = {10000, 50000, 100000, 200000};
    double[] expectedMinScores = {0.5, 0.7, 0.8, 0.9};

    for (int i = 0; i < amounts.length; i++) {
      double riskScore = FraudDetector.calculateRiskScore(amounts[i], true);

      assertThat(riskScore)
          .as("Risk score for amount %.2f", amounts[i])
          .isGreaterThanOrEqualTo(expectedMinScores[i]);
    }
  }

  @Test
  @DisplayName("Should apply first-order bonus to risk score")
  void testFirstOrderRiskBonus() {
    double amount = 60000;

    double firstOrderRisk = FraudDetector.calculateRiskScore(amount, true);
    double existingUserRisk = FraudDetector.calculateRiskScore(amount, false);

    assertThat(firstOrderRisk).isGreaterThan(existingUserRisk);
  }

  @Test
  @DisplayName("Should create properly formatted fraud alert")
  void testAlertCreation() {
    FraudAlert alert =
        FraudDetector.createAlert(
            highValueOrder,
            "HIGH_VALUE_FIRST_ORDER",
            0.85,
            "First order exceeds 50,000 TRY threshold");

    assertThat(alert).isNotNull();
    assertThat(alert.getAlertId()).isNotNull().isNotEmpty();
    assertThat(alert.getUserId()).isEqualTo("USR-NEW");
    assertThat(alert.getOrderId()).isEqualTo("ORD-002");
    assertThat(alert.getAlertType()).isEqualTo("HIGH_VALUE_FIRST_ORDER");
    assertThat(alert.getRiskScore()).isEqualTo(0.85);
    assertThat(alert.getAmount()).isEqualTo(75000.0);
    assertThat(alert.getTimestamp()).isNotNull();
  }

  @Test
  @DisplayName("Should generate unique alert IDs")
  void testUniqueAlertIds() {
    FraudAlert alert1 = FraudDetector.createAlert(highValueOrder, "TEST", 0.5, "Test 1");
    FraudAlert alert2 = FraudDetector.createAlert(highValueOrder, "TEST", 0.5, "Test 2");

    assertThat(alert1.getAlertId()).isNotEqualTo(alert2.getAlertId());
  }

  @Test
  @DisplayName("Should handle null user ID gracefully")
  void testNullUserId() {
    OrderEvent nullUserOrder =
        createOrderEvent("ORD-NULL", null, 1000.0, Instant.now().toEpochMilli());

    FraudDetector.HighValueFirstOrderDetector detector =
        new FraudDetector.HighValueFirstOrderDetector();

    assertThat(detector.processOrder(nullUserOrder, null)).isEmpty();
  }

  @Test
  @DisplayName("Should handle zero amount orders")
  void testZeroAmountOrder() {
    double riskScore = FraudDetector.calculateRiskScore(0.0, true);

    assertThat(riskScore).isEqualTo(0.0);
  }

  @Test
  @DisplayName("Should handle negative amounts as fraud")
  void testNegativeAmountOrder() {
    double riskScore = FraudDetector.calculateRiskScore(-5000.0, false);

    assertThat(riskScore).isGreaterThanOrEqualTo(0.9);
  }
}
