package com.strapdata.basketapp;

import com.datastax.driver.mapping.Result;
import com.strapdata.basketapp.model.Basket;
import com.strapdata.basketapp.model.BasketStatus;
import com.strapdata.basketapp.utils.TransformedListenableFuture;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.validation.Validated;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

@Controller("/basket")
@Validated
public class BasketController {

    private static final Logger logger = LoggerFactory.getLogger(BasketController.class);

    ElassandraStorage storage;
    BasketAccessor    basketAccessor;

    public BasketController(ElassandraStorage storage) {
        this.storage = storage;
        this.basketAccessor = storage.getMappingManager().createAccessor(BasketAccessor.class);
    }

    @Get("/")
    public HttpStatus index() {
        return HttpStatus.OK;
    }

    @Get(uri = "/{id}")
    public Maybe<Basket> getById(@QueryValue("id") UUID id) {
        return Maybe.fromFuture(storage.getMapper(Basket.class).getAsync(id));
    }

    @Get(uri = "/search")
    public Single<List<Basket>> getByStoreAndProduct(@Nullable @QueryValue("store_code") String storeCode, @Nullable @QueryValue("product_code") String productCode) {
        String esQuery = BasketAccessor.storeAndProductQuery(storeCode, productCode);
        return Single.fromFuture(new TransformedListenableFuture<Result<Basket>, List<Basket>>(this.basketAccessor.getByElasticsearchQueryAsync(esQuery), Result::all));
    }
}
