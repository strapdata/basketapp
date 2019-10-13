package com.strapdata.basketapp;


import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.extras.codecs.enums.EnumNameCodec;
import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.MappingManager;
import com.strapdata.basketapp.config.ElasticsearchConfiguration;
import com.strapdata.basketapp.model.Basket;
import com.strapdata.basketapp.model.BasketStatus;
import com.strapdata.basketapp.utils.DateTimeCodec;
import com.strapdata.basketapp.utils.ElassandraSecurity;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.env.Environment;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Context
public class ElassandraStorage {
    private static final Logger logger = LoggerFactory.getLogger(ElassandraStorage.class);

    public static final String KEYSPACE = "baskets";

    ElasticsearchConfiguration esConfig;
    Cluster cluster;
    Session session;
    MappingManager mappingManager;
    AtomicBoolean opened = new AtomicBoolean(false);
    AtomicBoolean initialized = new AtomicBoolean(false);

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private final ConcurrentMap<Class, Mapper> mappers = new ConcurrentHashMap<>();

    public ElassandraStorage(Environment env, Cluster cluster, ElasticsearchConfiguration elasticsearchConfig) {
        this.cluster = cluster;
        this.esConfig = elasticsearchConfig;

        // register codec
        CodecRegistry codecRegistry = cluster.getConfiguration().getCodecRegistry();
        codecRegistry.register(new DateTimeCodec());
        codecRegistry.register(new EnumNameCodec<BasketStatus>(BasketStatus.class));

        if (!env.getActiveNames().contains("test"))
            open();
    }

    // open driver connection
    public void open() {
        if (opened.compareAndSet(false, true)) {
            session = cluster.connect();
            session.execute(String.format(Locale.ROOT,
                "CREATE KEYSPACE IF NOT EXISTS %s WITH replication={'class' : 'NetworkTopologyStrategy', '%s':'1'} AND durable_writes = false",
                KEYSPACE, session.getCluster().getMetadata().getAllHosts().iterator().next().getDatacenter()));
            session.execute(String.format(Locale.ROOT, "USE %s", KEYSPACE));
            mappingManager = new MappingManager(session);
            logger.info("Elassandra storage session opened");
        }
        init();
    }

    // init CQL schema, Elasticsearch indices and data
    public void init() {
        if (initialized.compareAndSet(false, true)) {
            try {
                initSchema();
                initElasticsearch();
                initModel();
                logger.info("Elassandra storage initialized");
            } catch (Exception e) {
                logger.error("error:", e);
            }
        }
    }

    // clean up data between tests
    public void cleanup() {
        try {
            if (getSession() != null) {
                for(Row row : getSession().execute("SELECT table_name FROM system_schema.tables WHERE keyspace_name = ?", KEYSPACE))
                    getSession().execute(String.format(Locale.ROOT,"TRUNCATE %s.%s", KEYSPACE, row.getString("table_name")));
            }
            logger.info("Elassandra storage cleaned");
        } catch (Exception e) {
            logger.error("error:",e);
        }
    }

    // close driver connection and clean mappers.
    public void close() {
        if (opened.compareAndSet(true, false)) {
            session.close();
            mappingManager = null;
            session = null;
            logger.info("Elassandra storage closed");
        }
    }

    public Session getSession() {
        return session;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> Mapper<T> getMapper(Class<T> clazz) {
        Objects.requireNonNull(mappingManager);
        return mappers.computeIfAbsent(clazz, k -> mappingManager.mapper(k));
    }

    public MappingManager getMappingManager() {
        Objects.requireNonNull(mappingManager);
        return this.mappingManager;
    }

    public void initSchema() throws Exception {
        InputStream is = ElassandraStorage.class.getResourceAsStream("/schema.cql");
        if (is == null)
            throw new Exception("schema.cql not found in classpath");

        LineNumberReader reader = new LineNumberReader(new InputStreamReader(is));
        String line, content = "";
        do {
            line = reader.readLine();
            if (line != null && line.startsWith("//"))
                continue;
            if (line != null)
                content += line.replace('\n', ' ');
            if (content.endsWith(";")) {
                logger.info(content);
                session.execute(content);
                content = "";
            }
        } while (line != null);
        logger.info("CQL schema sucessfully initialized");
    }

    // Create ES indices
    public void initElasticsearch() throws IOException {
        logger.info("Init Elasticsearch {}://{}:{}", esConfig.scheme, esConfig.host, esConfig.port);
        RestClientBuilder clientBuilder = RestClient.builder(new HttpHost(esConfig.host, esConfig.port, esConfig.scheme));

        String index = KEYSPACE; // By default, index name is keyspace name

        ElassandraSecurity elassandraSecurity = new ElassandraSecurity();
        try(RestHighLevelClient client = new RestHighLevelClient(
                RestClient.builder(new HttpHost(esConfig.host, esConfig.port, esConfig.scheme))
                        .setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                            @Override
                            public HttpAsyncClientBuilder customizeHttpClient(
                                    HttpAsyncClientBuilder httpClientBuilder) {
                                if (elassandraSecurity.getUsername().isPresent() && elassandraSecurity.getPassword().isPresent()) {
                                    final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                                    credentialsProvider.setCredentials(AuthScope.ANY,
                                            new UsernamePasswordCredentials(elassandraSecurity.getUsername().get(), elassandraSecurity.getPassword().get()));
                                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                                }

                                if (elassandraSecurity.getSSLContextOption().isPresent()) {
                                    httpClientBuilder.setSSLContext(elassandraSecurity.getSSLContextOption().get());
                                    // TODO: fix this workaround
                                    httpClientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                                }
                                return httpClientBuilder;
                            }
                        }))
        ) {
            CreateIndexRequest request = new CreateIndexRequest(KEYSPACE);
            XContentBuilder mappingBuilder = XContentFactory.jsonBuilder();
            mappingBuilder.startObject();
            {
                mappingBuilder.startObject(index);
                {
                    mappingBuilder.field("discover", ".*");
                }
                mappingBuilder.endObject();
            }
            mappingBuilder.endObject();
            request.mapping(index, mappingBuilder);
            request.settings(Settings.builder()
                .put("keyspace", session.getLoggedKeyspace())
                .put("synchronous_refresh", true)   // for testing only
                .build());
            CreateIndexResponse createIndexResponse = client.indices().create(request);
            logger.info("Elasticsearch index {} created", index);
        }
    }

    public void initModel() throws IOException {

    }
}
