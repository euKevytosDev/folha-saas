package com.sacolao.order.entity;

import com.sacolao.customer.entity.Customer;
import com.sacolao.establishment.entity.Establishment;
import com.sacolao.fiscal.entity.NfceStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establishment_id", nullable = false)
    private Establishment establishment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "public_code", nullable = false, unique = true, length = 12)
    private String publicCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", nullable = false, length = 16)
    private FulfillmentType fulfillmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 24)
    private PaymentMethod paymentMethod;

    @Column(name = "customer_name", nullable = false, length = 160)
    private String customerName;

    @Column(name = "customer_phone", nullable = false, length = 32)
    private String customerPhone;

    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    @Column(name = "address_zip_code", length = 16)
    private String addressZipCode;

    @Column(name = "address_street", length = 255)
    private String addressStreet;

    @Column(name = "address_number", length = 32)
    private String addressNumber;

    @Column(name = "address_complement", length = 120)
    private String addressComplement;

    @Column(name = "address_neighborhood", length = 120)
    private String addressNeighborhood;

    @Column(name = "address_city", length = 120)
    private String addressCity;

    @Column(name = "address_state", length = 2)
    private String addressState;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(name = "coupon_id")
    private UUID couponId;

    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "nfce_ref", length = 80)
    private String nfceRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "nfce_status", nullable = false, length = 24)
    private NfceStatus nfceStatus = NfceStatus.NONE;

    @Column(name = "nfce_number", length = 20)
    private String nfceNumber;

    @Column(name = "nfce_series", length = 10)
    private String nfceSeries;

    @Column(name = "nfce_chave", length = 50)
    private String nfceChave;

    @Column(name = "nfce_url_danfe", length = 500)
    private String nfceUrlDanfe;

    @Column(name = "nfce_url_xml", length = 500)
    private String nfceUrlXml;

    @Column(name = "nfce_qrcode_url", length = 500)
    private String nfceQrcodeUrl;

    @Column(name = "nfce_error_message", columnDefinition = "text")
    private String nfceErrorMessage;

    @Column(name = "nfce_emitted_at")
    private Instant nfceEmittedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
        item.setEstablishment(establishment);
    }

    public UUID getEstablishmentId() {
        return establishment == null ? null : establishment.getId();
    }

    public UUID getId() {
        return id;
    }

    public Establishment getEstablishment() {
        return establishment;
    }

    public void setEstablishment(Establishment establishment) {
        this.establishment = establishment;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public String getPublicCode() {
        return publicCode;
    }

    public void setPublicCode(String publicCode) {
        this.publicCode = publicCode;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public FulfillmentType getFulfillmentType() {
        return fulfillmentType;
    }

    public void setFulfillmentType(FulfillmentType fulfillmentType) {
        this.fulfillmentType = fulfillmentType;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getAddressZipCode() {
        return addressZipCode;
    }

    public void setAddressZipCode(String addressZipCode) {
        this.addressZipCode = addressZipCode;
    }

    public String getAddressStreet() {
        return addressStreet;
    }

    public void setAddressStreet(String addressStreet) {
        this.addressStreet = addressStreet;
    }

    public String getAddressNumber() {
        return addressNumber;
    }

    public void setAddressNumber(String addressNumber) {
        this.addressNumber = addressNumber;
    }

    public String getAddressComplement() {
        return addressComplement;
    }

    public void setAddressComplement(String addressComplement) {
        this.addressComplement = addressComplement;
    }

    public String getAddressNeighborhood() {
        return addressNeighborhood;
    }

    public void setAddressNeighborhood(String addressNeighborhood) {
        this.addressNeighborhood = addressNeighborhood;
    }

    public String getAddressCity() {
        return addressCity;
    }

    public void setAddressCity(String addressCity) {
        this.addressCity = addressCity;
    }

    public String getAddressState() {
        return addressState;
    }

    public void setAddressState(String addressState) {
        this.addressState = addressState;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee;
    }

    public void setDeliveryFee(BigDecimal deliveryFee) {
        this.deliveryFee = deliveryFee;
    }

    public UUID getCouponId() {
        return couponId;
    }

    public void setCouponId(UUID couponId) {
        this.couponId = couponId;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getNfceRef() {
        return nfceRef;
    }

    public void setNfceRef(String nfceRef) {
        this.nfceRef = nfceRef;
    }

    public NfceStatus getNfceStatus() {
        return nfceStatus;
    }

    public void setNfceStatus(NfceStatus nfceStatus) {
        this.nfceStatus = nfceStatus == null ? NfceStatus.NONE : nfceStatus;
    }

    public String getNfceNumber() {
        return nfceNumber;
    }

    public void setNfceNumber(String nfceNumber) {
        this.nfceNumber = nfceNumber;
    }

    public String getNfceSeries() {
        return nfceSeries;
    }

    public void setNfceSeries(String nfceSeries) {
        this.nfceSeries = nfceSeries;
    }

    public String getNfceChave() {
        return nfceChave;
    }

    public void setNfceChave(String nfceChave) {
        this.nfceChave = nfceChave;
    }

    public String getNfceUrlDanfe() {
        return nfceUrlDanfe;
    }

    public void setNfceUrlDanfe(String nfceUrlDanfe) {
        this.nfceUrlDanfe = nfceUrlDanfe;
    }

    public String getNfceUrlXml() {
        return nfceUrlXml;
    }

    public void setNfceUrlXml(String nfceUrlXml) {
        this.nfceUrlXml = nfceUrlXml;
    }

    public String getNfceQrcodeUrl() {
        return nfceQrcodeUrl;
    }

    public void setNfceQrcodeUrl(String nfceQrcodeUrl) {
        this.nfceQrcodeUrl = nfceQrcodeUrl;
    }

    public String getNfceErrorMessage() {
        return nfceErrorMessage;
    }

    public void setNfceErrorMessage(String nfceErrorMessage) {
        this.nfceErrorMessage = nfceErrorMessage;
    }

    public Instant getNfceEmittedAt() {
        return nfceEmittedAt;
    }

    public void setNfceEmittedAt(Instant nfceEmittedAt) {
        this.nfceEmittedAt = nfceEmittedAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
