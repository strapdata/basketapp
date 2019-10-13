package com.strapdata.basketapp.utils;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ProtocolVersion;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;

// Cassandra cluster builder programmatic customisation
@Singleton
class ClusterBuilderListener implements BeanCreatedEventListener<Cluster.Builder> {
    private static final Logger logger = LoggerFactory.getLogger(ClusterBuilderListener.class);

    @Override
    public Cluster.Builder onCreated(BeanCreatedEvent<Cluster.Builder> event) {
        Cluster.Builder builder = event.getBean();
        ApplicationContext applicationContext = (ApplicationContext) event.getSource();

        // see https://docs.datastax.com/en/developer/driver-matrix/doc/common/driverMatrix.html
        // see https://github.com/datastax/java-driver/tree/3.0/manual/native_protocol
        builder.withProtocolVersion(ProtocolVersion.V4); // compatible cassandra 2.1,

        ElassandraSecurity elassandraSecurity = new ElassandraSecurity();
        if (elassandraSecurity.getUsername().isPresent()) {
            logger.info("Cassandra username={}", elassandraSecurity.getUsername().get());
            builder.withCredentials(elassandraSecurity.getUsername().get(), elassandraSecurity.getPassword().get());
        }

        if (elassandraSecurity.getSSLOptions().isPresent()) {
            builder.withSSL(elassandraSecurity.getSSLOptions().get());
            logger.info("SSL options succefully built");
        }
        return builder;
    }
}
