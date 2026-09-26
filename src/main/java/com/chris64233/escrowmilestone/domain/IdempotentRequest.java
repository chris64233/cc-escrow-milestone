package com.chris64233.escrowmilestone.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 幂等请求记录：同一业务号的同一类请求只处理一次。
 *
 * <p>{@code requestFingerprint} 为请求内容指纹：业务号重复且内容一致时返回首次结果；
 * 业务号重复但内容变化时判定为冲突并拒绝。
 */
@Entity
@Table(name = "idempotent_request", uniqueConstraints =
        @jakarta.persistence.UniqueConstraint(name = "uk_idem_type_business_no",
                columnNames = {"request_type", "business_no"}))
public class IdempotentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, updatable = false, length = 16)
    private IdempotentRequestType requestType;

    @Column(name = "business_no", nullable = false, updatable = false, length = 64)
    private String businessNo;

    @Column(name = "request_fingerprint", nullable = false, updatable = false, length = 128)
    private String requestFingerprint;

    /** 首次处理产出的业务号（如放款业务号）。 */
    @Column(name = "result_ref", updatable = false, length = 64)
    private String resultRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotentRequest() {
    }

    public IdempotentRequest(IdempotentRequestType requestType, String businessNo,
                             String requestFingerprint, String resultRef, Instant createdAt) {
        this.requestType = requestType;
        this.businessNo = businessNo;
        this.requestFingerprint = requestFingerprint;
        this.resultRef = resultRef;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public IdempotentRequestType getRequestType() {
        return requestType;
    }

    public String getBusinessNo() {
        return businessNo;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public String getResultRef() {
        return resultRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
