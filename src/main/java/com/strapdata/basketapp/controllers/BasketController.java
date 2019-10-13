package com.strapdata.basketapp.controllers;

import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.Result;
import com.strapdata.basketapp.ElassandraStorage;
import com.strapdata.basketapp.model.Basket;
import com.strapdata.basketapp.model.BasketAccessor;
import com.strapdata.basketapp.utils.TransformedListenableFuture;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

@Controller("/basketapp/basket")
public class BasketController {

    private static final Logger logger = LoggerFactory.getLogger(BasketController.class);

    ElassandraStorage storage;
    BasketAccessor basketAccessor;

    public BasketController(ElassandraStorage storage) {
        this.storage = storage;
        this.basketAccessor = storage.getMappingManager().createAccessor(BasketAccessor.class);
    }

    /**
     * Health check
     * @return
     */
    @Get("/")
    public HttpStatus index() {
        return HttpStatus.OK;
    }

    /**
     * Get a basket by id.
     * @param id
     * @return
     */
    @Get(uri = "/{id}")
    public Maybe<Basket> getById(@QueryValue("id") UUID id) {
        return Maybe.fromFuture(storage.getMapper(Basket.class).getAsync(id));
    }

    /**
     * Search for baskets matching the store code and product code.
     * @param storeCode
     * @param productCode
     * @return
     */
    @Get(uri = "/search", consumes = MediaType.APPLICATION_FORM_URLENCODED)
    public Single<List<Basket>> getByStoreAndProduct(@Nullable @QueryValue("store_code") String storeCode,
                                                     @Nullable @QueryValue("product_code") String productCode) {
        String esQuery = BasketAccessor.storeAndProductQuery(storeCode, productCode);
        return Single.fromFuture(new TransformedListenableFuture<Result<Basket>, List<Basket>>(this.basketAccessor.getByElasticsearchQueryAsync(esQuery), Result::all));
    }

    /**
     * Bulk upload data.
     * @param file
     * @return
     */
    @Post(value = "/", consumes = MediaType.MULTIPART_FORM_DATA)
    public HttpStatus upload(CompletedFileUpload file) {
        logger.debug("receiving file={} content-type={}", file.getFilename(), file.getContentType());
        return HttpStatus.OK;
    }

    @Post(value = "/", consumes = MediaType.APPLICATION_JSON)
    public Single<HttpStatus> insert(@Body Basket basket) {
        logger.debug("insert basket={}", basket);
        return Completable.fromFuture(storage.getMapper(Basket.class).saveAsync(basket)).toSingleDefault(HttpStatus.ACCEPTED);
    }
}
