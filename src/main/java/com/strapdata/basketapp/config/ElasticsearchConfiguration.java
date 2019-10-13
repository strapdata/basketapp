package com.strapdata.basketapp.config;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("elasticsearch")
public class ElasticsearchConfiguration {

    public String scheme = "http";
    public String host = "localhost";
    public int port = 9200;

}
