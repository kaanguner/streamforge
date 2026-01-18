package com.trendstream.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

/** Clickstream Event POJO Represents user interactions from the e-commerce platform. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClickstreamEvent implements Serializable {

  private static final long serialVersionUID = 1L;

  @JsonProperty("event_id")
  private String eventId;

  @JsonProperty("user_id")
  private String userId;

  @JsonProperty("session_id")
  private String sessionId;

  @JsonProperty("event_type")
  private String eventType;

  @JsonProperty("product_id")
  private String productId;

  @JsonProperty("category")
  private String category;

  @JsonProperty("price")
  private Double price;

  @JsonProperty("quantity")
  private Integer quantity;

  @JsonProperty("timestamp")
  private Long timestamp;

  @JsonProperty("device_type")
  private String deviceType;

  @JsonProperty("geo_region")
  private String geoRegion;

  @JsonProperty("page_url")
  private String pageUrl;

  @JsonProperty("referrer")
  private String referrer;

  // Default constructor for Jackson
  public ClickstreamEvent() {}

  // Getters and Setters
  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getProductId() {
    return productId;
  }

  public void setProductId(String productId) {
    this.productId = productId;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public Double getPrice() {
    return price;
  }

  public void setPrice(Double price) {
    this.price = price;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public void setQuantity(Integer quantity) {
    this.quantity = quantity;
  }

  public Long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Long timestamp) {
    this.timestamp = timestamp;
  }

  public String getDeviceType() {
    return deviceType;
  }

  public void setDeviceType(String deviceType) {
    this.deviceType = deviceType;
  }

  public String getGeoRegion() {
    return geoRegion;
  }

  public void setGeoRegion(String geoRegion) {
    this.geoRegion = geoRegion;
  }

  public String getPageUrl() {
    return pageUrl;
  }

  public void setPageUrl(String pageUrl) {
    this.pageUrl = pageUrl;
  }

  public String getReferrer() {
    return referrer;
  }

  public void setReferrer(String referrer) {
    this.referrer = referrer;
  }

  @Override
  public String toString() {
    return "ClickstreamEvent{"
        + "eventId='"
        + eventId
        + '\''
        + ", userId='"
        + userId
        + '\''
        + ", eventType='"
        + eventType
        + '\''
        + ", productId='"
        + productId
        + '\''
        + ", timestamp="
        + timestamp
        + '}';
  }
}
