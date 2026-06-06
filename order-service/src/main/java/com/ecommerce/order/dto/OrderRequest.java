package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * @param simulateFailure demo-only switch. When true, the saga's confirm step
 *                        throws, forcing the compensating rollback to run.
 *                        Defaults to false when omitted from the JSON body.
 */
public record OrderRequest(
        @NotBlank String skuCode,
        @NotNull @Positive Integer quantity,
        boolean simulateFailure
) {
}
