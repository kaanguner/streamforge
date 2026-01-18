package com.trendstream.model;

import java.io.Serializable;

/** Session Aggregate Represents aggregated session data computed by SessionAggregator. */
public class SessionAggregate implements Serializable {

  private static final long serialVersionUID = 1L;

  private String userId;
  private String sessionId;
  private Long sessionStart;
  private Long sessionEnd;
  private Long durationSeconds;
  private Integer pageViews;
  private Integer productViews;
  private Integer addToCartCount;
  private Integer purchaseCount;
  private Double totalRevenue;
  private String deviceType;
  private String geoRegion;
  private String primaryCategory;

  public SessionAggregate() {}

  public SessionAggregate(String userId, String sessionId) {
    this.userId = userId;
    this.sessionId = sessionId;
    this.pageViews = 0;
    this.productViews = 0;
    this.addToCartCount = 0;
    this.purchaseCount = 0;
    this.totalRevenue = 0.0;
  }

  // Getters and Setters
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

  public Long getSessionStart() {
    return sessionStart;
  }

  public void setSessionStart(Long sessionStart) {
    this.sessionStart = sessionStart;
  }

  public Long getSessionEnd() {
    return sessionEnd;
  }

  public void setSessionEnd(Long sessionEnd) {
    this.sessionEnd = sessionEnd;
  }

  public Long getDurationSeconds() {
    return durationSeconds;
  }

  public void setDurationSeconds(Long durationSeconds) {
    this.durationSeconds = durationSeconds;
  }

  public Integer getPageViews() {
    return pageViews;
  }

  public void setPageViews(Integer pageViews) {
    this.pageViews = pageViews;
  }

  public Integer getProductViews() {
    return productViews;
  }

  public void setProductViews(Integer productViews) {
    this.productViews = productViews;
  }

  public Integer getAddToCartCount() {
    return addToCartCount;
  }

  public void setAddToCartCount(Integer addToCartCount) {
    this.addToCartCount = addToCartCount;
  }

  public Integer getPurchaseCount() {
    return purchaseCount;
  }

  public void setPurchaseCount(Integer purchaseCount) {
    this.purchaseCount = purchaseCount;
  }

  public Double getTotalRevenue() {
    return totalRevenue;
  }

  public void setTotalRevenue(Double totalRevenue) {
    this.totalRevenue = totalRevenue;
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

  public String getPrimaryCategory() {
    return primaryCategory;
  }

  public void setPrimaryCategory(String primaryCategory) {
    this.primaryCategory = primaryCategory;
  }

  /** Calculate conversion rate (purchases / product views) */
  public Double getConversionRate() {
    if (productViews == null || productViews == 0) return 0.0;
    return (double) purchaseCount / productViews;
  }

  @Override
  public String toString() {
    return "SessionAggregate{"
        + "userId='"
        + userId
        + '\''
        + ", sessionId='"
        + sessionId
        + '\''
        + ", duration="
        + durationSeconds
        + "s"
        + ", pageViews="
        + pageViews
        + ", purchases="
        + purchaseCount
        + ", revenue="
        + totalRevenue
        + '}';
  }
}
