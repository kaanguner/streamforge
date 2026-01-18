package com.trendstream.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.List;

/** Order Event POJO Represents order lifecycle events from the e-commerce platform. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEvent implements Serializable {

  private static final long serialVersionUID = 1L;

  @JsonProperty("order_id")
  private String orderId;

  @JsonProperty("user_id")
  private String userId;

  @JsonProperty("session_id")
  private String sessionId;

  @JsonProperty("order_status")
  private String orderStatus;

  @JsonProperty("items")
  private List<OrderItem> items;

  @JsonProperty("subtotal")
  private Double subtotal;

  @JsonProperty("discount_amount")
  private Double discountAmount;

  @JsonProperty("shipping_cost")
  private Double shippingCost;

  @JsonProperty("total_amount")
  private Double totalAmount;

  @JsonProperty("currency")
  private String currency;

  @JsonProperty("payment_method")
  private String paymentMethod;

  @JsonProperty("shipping_address_region")
  private String shippingAddressRegion;

  @JsonProperty("timestamp")
  private Long timestamp;

  @JsonProperty("is_first_order")
  private Boolean isFirstOrder;

  // Default constructor
  public OrderEvent() {}

  // Getters and Setters
  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
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

  public String getOrderStatus() {
    return orderStatus;
  }

  public void setOrderStatus(String orderStatus) {
    this.orderStatus = orderStatus;
  }

  public List<OrderItem> getItems() {
    return items;
  }

  public void setItems(List<OrderItem> items) {
    this.items = items;
  }

  public Double getSubtotal() {
    return subtotal;
  }

  public void setSubtotal(Double subtotal) {
    this.subtotal = subtotal;
  }

  public Double getDiscountAmount() {
    return discountAmount;
  }

  public void setDiscountAmount(Double discountAmount) {
    this.discountAmount = discountAmount;
  }

  public Double getShippingCost() {
    return shippingCost;
  }

  public void setShippingCost(Double shippingCost) {
    this.shippingCost = shippingCost;
  }

  public Double getTotalAmount() {
    return totalAmount;
  }

  public void setTotalAmount(Double totalAmount) {
    this.totalAmount = totalAmount;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getPaymentMethod() {
    return paymentMethod;
  }

  public void setPaymentMethod(String paymentMethod) {
    this.paymentMethod = paymentMethod;
  }

  public String getShippingAddressRegion() {
    return shippingAddressRegion;
  }

  public void setShippingAddressRegion(String shippingAddressRegion) {
    this.shippingAddressRegion = shippingAddressRegion;
  }

  public Long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Long timestamp) {
    this.timestamp = timestamp;
  }

  public Boolean getIsFirstOrder() {
    return isFirstOrder;
  }

  public void setIsFirstOrder(Boolean isFirstOrder) {
    this.isFirstOrder = isFirstOrder;
  }

  /** Order Item nested class */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class OrderItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("product_id")
    private String productId;

    @JsonProperty("product_name")
    private String productName;

    @JsonProperty("category")
    private String category;

    @JsonProperty("quantity")
    private Integer quantity;

    @JsonProperty("unit_price")
    private Double unitPrice;

    @JsonProperty("total_price")
    private Double totalPrice;

    public OrderItem() {}

    public String getProductId() {
      return productId;
    }

    public void setProductId(String productId) {
      this.productId = productId;
    }

    public String getProductName() {
      return productName;
    }

    public void setProductName(String productName) {
      this.productName = productName;
    }

    public String getCategory() {
      return category;
    }

    public void setCategory(String category) {
      this.category = category;
    }

    public Integer getQuantity() {
      return quantity;
    }

    public void setQuantity(Integer quantity) {
      this.quantity = quantity;
    }

    public Double getUnitPrice() {
      return unitPrice;
    }

    public void setUnitPrice(Double unitPrice) {
      this.unitPrice = unitPrice;
    }

    public Double getTotalPrice() {
      return totalPrice;
    }

    public void setTotalPrice(Double totalPrice) {
      this.totalPrice = totalPrice;
    }
  }

  @Override
  public String toString() {
    return "OrderEvent{"
        + "orderId='"
        + orderId
        + '\''
        + ", userId='"
        + userId
        + '\''
        + ", totalAmount="
        + totalAmount
        + ", isFirstOrder="
        + isFirstOrder
        + '}';
  }
}
