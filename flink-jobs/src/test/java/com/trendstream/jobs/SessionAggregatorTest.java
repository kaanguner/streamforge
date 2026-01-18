package com.trendstream.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.trendstream.model.ClickstreamEvent;
import com.trendstream.model.SessionAggregate;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for the SessionAggregator Flink job. */
public class SessionAggregatorTest {

  private ClickstreamEvent pageViewEvent;
  private ClickstreamEvent productViewEvent;
  private ClickstreamEvent addToCartEvent;
  private ClickstreamEvent purchaseEvent;

  @BeforeEach
  void setUp() {
    long baseTime = Instant.now().toEpochMilli();

    pageViewEvent = createEvent("USR-001", "SESS-001", "page_view", baseTime, 0);
    productViewEvent = createEvent("USR-001", "SESS-001", "product_view", baseTime + 10000, 0);
    addToCartEvent = createEvent("USR-001", "SESS-001", "add_to_cart", baseTime + 20000, 2499.0);
    purchaseEvent = createEvent("USR-001", "SESS-001", "purchase", baseTime + 30000, 2499.0);
  }

  private ClickstreamEvent createEvent(
      String userId, String sessionId, String eventType, long timestamp, double price) {
    ClickstreamEvent event = new ClickstreamEvent();
    event.setUserId(userId);
    event.setSessionId(sessionId);
    event.setEventType(eventType);
    event.setTimestamp(timestamp);
    event.setProductPrice(price);
    event.setProductId("PROD-001");
    event.setCategory("Elektronik");
    return event;
  }

  @Test
  @DisplayName("Should correctly create initial accumulator")
  void testCreateAccumulator() {
    SessionAggregator.SessionAccumulator acc = new SessionAggregator.SessionAccumulator();

    assertThat(acc.getPageViews()).isEqualTo(0);
    assertThat(acc.getProductViews()).isEqualTo(0);
    assertThat(acc.getAddToCarts()).isEqualTo(0);
    assertThat(acc.getPurchases()).isEqualTo(0);
    assertThat(acc.getRevenue()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("Should accumulate page view events")
  void testAccumulatePageViews() {
    SessionAggregator.SessionAccumulator acc = new SessionAggregator.SessionAccumulator();

    acc.add(pageViewEvent);
    acc.add(createEvent("USR-001", "SESS-001", "page_view", Instant.now().toEpochMilli(), 0));

    assertThat(acc.getPageViews()).isEqualTo(2);
    assertThat(acc.getProductViews()).isEqualTo(0);
  }

  @Test
  @DisplayName("Should accumulate product view events")
  void testAccumulateProductViews() {
    SessionAggregator.SessionAccumulator acc = new SessionAggregator.SessionAccumulator();

    acc.add(productViewEvent);

    assertThat(acc.getProductViews()).isEqualTo(1);
  }

  @Test
  @DisplayName("Should accumulate add_to_cart events")
  void testAccumulateAddToCarts() {
    SessionAggregator.SessionAccumulator acc = new SessionAggregator.SessionAccumulator();

    acc.add(addToCartEvent);
    acc.add(addToCartEvent);

    assertThat(acc.getAddToCarts()).isEqualTo(2);
  }

  @Test
  @DisplayName("Should accumulate purchases with revenue")
  void testAccumulatePurchases() {
    SessionAggregator.SessionAccumulator acc = new SessionAggregator.SessionAccumulator();

    acc.add(purchaseEvent);

    assertThat(acc.getPurchases()).isEqualTo(1);
    assertThat(acc.getRevenue()).isEqualTo(2499.0);
  }

  @Test
  @DisplayName("Should accumulate full user journey")
  void testFullUserJourney() {
    SessionAggregator.SessionAccumulator acc = new SessionAggregator.SessionAccumulator();

    acc.add(pageViewEvent);
    acc.add(productViewEvent);
    acc.add(productViewEvent);
    acc.add(addToCartEvent);
    acc.add(purchaseEvent);

    assertThat(acc.getPageViews()).isEqualTo(1);
    assertThat(acc.getProductViews()).isEqualTo(2);
    assertThat(acc.getAddToCarts()).isEqualTo(1);
    assertThat(acc.getPurchases()).isEqualTo(1);
    assertThat(acc.getRevenue()).isEqualTo(2499.0);
  }

  @Test
  @DisplayName("Should create correct SessionAggregate from accumulator")
  void testGetResult() {
    SessionAggregator.SessionAccumulator acc = new SessionAggregator.SessionAccumulator();
    acc.setUserId("USR-001");
    acc.setSessionId("SESS-001");
    acc.add(pageViewEvent);
    acc.add(productViewEvent);
    acc.add(purchaseEvent);

    SessionAggregate result = acc.toSessionAggregate();

    assertThat(result.getUserId()).isEqualTo("USR-001");
    assertThat(result.getSessionId()).isEqualTo("SESS-001");
    assertThat(result.getPageViews()).isEqualTo(1);
    assertThat(result.getProductViews()).isEqualTo(1);
    assertThat(result.getPurchases()).isEqualTo(1);
    assertThat(result.getRevenue()).isEqualTo(2499.0);
  }

  @Test
  @DisplayName("Should calculate session duration correctly")
  void testSessionDuration() {
    long startTime = Instant.parse("2024-01-18T10:00:00Z").toEpochMilli();
    long endTime = Instant.parse("2024-01-18T10:30:00Z").toEpochMilli();

    SessionAggregator.SessionAccumulator acc = new SessionAggregator.SessionAccumulator();
    acc.add(createEvent("USR-001", "SESS-001", "page_view", startTime, 0));
    acc.add(createEvent("USR-001", "SESS-001", "purchase", endTime, 1000));

    SessionAggregate result = acc.toSessionAggregate();

    assertThat(result.getDurationSeconds()).isEqualTo(1800);
  }

  @Test
  @DisplayName("Should merge two accumulators correctly")
  void testMergeAccumulators() {
    SessionAggregator.SessionAccumulator acc1 = new SessionAggregator.SessionAccumulator();
    acc1.add(pageViewEvent);
    acc1.add(productViewEvent);

    SessionAggregator.SessionAccumulator acc2 = new SessionAggregator.SessionAccumulator();
    acc2.add(addToCartEvent);
    acc2.add(purchaseEvent);

    SessionAggregator.SessionAccumulator merged = acc1.merge(acc2);

    assertThat(merged.getPageViews()).isEqualTo(1);
    assertThat(merged.getProductViews()).isEqualTo(1);
    assertThat(merged.getAddToCarts()).isEqualTo(1);
    assertThat(merged.getPurchases()).isEqualTo(1);
    assertThat(merged.getRevenue()).isEqualTo(2499.0);
  }

  @Test
  @DisplayName("Should handle unknown event types gracefully")
  void testUnknownEventType() {
    ClickstreamEvent unknownEvent =
        createEvent("USR-001", "SESS-001", "unknown_type", Instant.now().toEpochMilli(), 0);

    SessionAggregator.SessionAccumulator acc = new SessionAggregator.SessionAccumulator();
    acc.add(unknownEvent);

    assertThat(acc.getPageViews()).isEqualTo(0);
    assertThat(acc.getProductViews()).isEqualTo(0);
    assertThat(acc.getPurchases()).isEqualTo(0);
  }

  @Test
  @DisplayName("Should handle empty session")
  void testEmptySession() {
    SessionAggregator.SessionAccumulator acc = new SessionAggregator.SessionAccumulator();
    acc.setUserId("USR-001");
    acc.setSessionId("SESS-EMPTY");

    SessionAggregate result = acc.toSessionAggregate();

    assertThat(result).isNotNull();
    assertThat(result.getPageViews()).isEqualTo(0);
    assertThat(result.getRevenue()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("Should handle null price in event")
  void testNullPrice() {
    ClickstreamEvent nullPriceEvent =
        createEvent("USR-001", "SESS-001", "purchase", Instant.now().toEpochMilli(), 0);
    nullPriceEvent.setProductPrice(null);

    SessionAggregator.SessionAccumulator acc = new SessionAggregator.SessionAccumulator();
    acc.add(nullPriceEvent);

    assertThat(acc.getPurchases()).isEqualTo(1);
    assertThat(acc.getRevenue()).isEqualTo(0.0);
  }
}
