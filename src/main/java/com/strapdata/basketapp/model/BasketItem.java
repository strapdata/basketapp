package com.strapdata.basketapp.model;

import com.datastax.driver.mapping.annotations.Field;
import com.datastax.driver.mapping.annotations.UDT;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.Wither;

@UDT(name="basket_item")
@Data
@Builder
@Wither
@ToString(includeFieldNames=true)
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BasketItem {

    @Field(name = "product_qty")
    @JsonProperty("product_qty")
    private Integer productQuantity;

    @Field(name = "amount_paid")
    @JsonProperty("amount_paid")
    private Double amountPaid;

    @Field(name = "product_code")
    @JsonProperty("product_code")
    private String productCode;
}
