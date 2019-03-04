package com.strapdata.basketapp;

import com.datastax.driver.mapping.Result;
import com.datastax.driver.mapping.annotations.Accessor;
import com.datastax.driver.mapping.annotations.Query;
import com.google.common.util.concurrent.ListenableFuture;
import com.strapdata.basketapp.model.Basket;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;

@Accessor
public interface BasketAccessor {

    @Query("SELECT * FROM baskets WHERE es_query = ? AND es_options='indices=baskets' LIMIT 500 ALLOW FILTERING")
    ListenableFuture<Result<Basket>> getByElasticsearchQueryAsync(String esQuery);


    public static String storeAndProductQuery(String storeCode, String productCode) {
        BoolQueryBuilder queryBuilder = new BoolQueryBuilder();

        if (storeCode != null)
            queryBuilder.filter(QueryBuilders.termQuery("store_code", storeCode));

        if (productCode != null)
            queryBuilder.filter(QueryBuilders.nestedQuery("items", QueryBuilders.termQuery("items.product_code", productCode), ScoreMode.Avg));

        if (!queryBuilder.hasClauses())
            queryBuilder.should(QueryBuilders.matchAllQuery());

        return new SearchSourceBuilder().query(queryBuilder).toString(ToXContent.EMPTY_PARAMS);
    }
}
