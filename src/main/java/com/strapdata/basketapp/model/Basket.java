package com.strapdata.basketapp.model;

import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.Wither;

import java.util.Date;
import java.util.List;
import java.util.UUID;


@Table(name = "baskets",
    readConsistency = "LOCAL_ONE",
    writeConsistency = "LOCAL_ONE",
    caseSensitiveKeyspace = false,
    caseSensitiveTable = false)
@Data
@Builder
@Wither
@ToString(includeFieldNames=true)
@AllArgsConstructor
@NoArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Basket {
    @PartitionKey(0)
    UUID id;

    @Column(name = "store_code")
    @JsonProperty("store_code")
    String storeCode;

    @Column(name = "basket_status")
    @JsonProperty("basket_status")
    BasketStatus basketStatus;

    @Column(name = "processing_date")
    @JsonProperty("processing_date")
    Date processingDate;

    List<BasketItem> items;
}
