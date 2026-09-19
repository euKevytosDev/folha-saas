package com.sacolao.store.dto;

import com.sacolao.order.entity.FulfillmentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CartQuoteRequest(
        @NotEmpty List<@Valid CartQuoteItemRequest> items,
        FulfillmentType fulfillmentType,
        @Size(max = 40) String couponCode
) {
}
