package org.cassandraunit;

import com.strapdata.basketapp.ElassandraStorage;
import org.cassandraunit.dataset.cql.SimpleCQLDataSet;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class ElassandraCQLUnit5 extends CassandraCQLUnit implements BeforeEachCallback, AfterEachCallback {

    public ElassandraCQLUnit5() {
        super(new SimpleCQLDataSet("SELECT * FROM system.peers", true, false, ElassandraStorage.KEYSPACE));

        // enable the Elasticsearch CQL query handler
        System.setProperty("cassandra.custom_query_handler_class","org.elassandra.index.ElasticQueryHandler");
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        super.after();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        super.before();
    }
}
