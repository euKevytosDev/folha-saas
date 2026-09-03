package com.sacolao.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CartQuoteRequest(@NotEmpty List<@Valid CartQuoteItemRequest> items) {
}
