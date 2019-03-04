package com.strapdata.basketapp;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("elasticsearch")
public class ElasticsearchConfiguration {

    String scheme = "http";
    String host = "localhost";
    int port = 9200;

}
