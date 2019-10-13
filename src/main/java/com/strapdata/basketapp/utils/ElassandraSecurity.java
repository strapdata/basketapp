package com.strapdata.basketapp.utils;

import com.datastax.driver.core.RemoteEndpointAwareJdkSSLOptions;
import com.datastax.driver.core.RemoteEndpointAwareNettySSLOptions;
import com.datastax.driver.core.SSLOptions;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;
import java.util.Optional;

import static java.nio.file.Paths.get;

/**
 * Setup SSL and Login/Password
 */
public class ElassandraSecurity {

    private static final Logger logger = LoggerFactory.getLogger(ElassandraSecurity.class);

    public static String getEnvOrDefault(String name, String defaut) {
        return System.getenv(name) == null ? defaut : System.getenv(name);
    }

    public static int getEnvOrDefaultAsInt(String name, String defaut) {
        return Integer.parseInt(getEnvOrDefault(name, defaut));
    }

    // WARNING: Config inject not working !!!!
    //@Property(name = "elassandra.ssl.truststore")
    String trustStorePath = System.getenv("ELASSANDRA_SSL_TRUSTSTORE");

    //@Value("${elassandra.ssl.truststorepass:changeit}")
    String trustStorePass = System.getenv("ELASSANDRA_SSL_TRUSTSTOREPASS");

    //@Value("${elassandra.ssl.keystore}")
    String keyStorePath;

    //@Value("${elassandra.ssl.keystorepass}")
    String keyStorePass;

    //@Property(name="elassandra.ssl.provider}")
    String sslProviderName = SslProvider.JDK.toString();

    //@Value("${elassandra.auth.username}")
    String elassandraUsername = System.getenv("ELASSANDRA_AUTH_USERNAME");

    //@Value("${elassandra.auth.password}")
    String elassandraPassword = System.getenv("ELASSANDRA_AUTH_PASSWORD");


    Optional<SSLOptions> sslOptions = Optional.empty();   // for cassandra
    Optional<SSLContext> sslContextOption = Optional.empty();   // for elasticsearch

    public ElassandraSecurity() {

        logger.info("Init security context, trustStorePath="+trustStorePath+" trustStorePass="+trustStorePass);
        if (trustStorePath != null && trustStorePath.length() > 0) {
            logger.info("Loading trustStorePath={}", trustStorePath);

            // Cassandra context
            SslProvider sslProvider = SslProvider.valueOf(sslProviderName);
            final SslContextBuilder sslContextBuilder = SslContextBuilder.forClient();
            sslContextBuilder.sslProvider(sslProvider);

            // Elasticsearch context
            final SSLContextBuilder sslBuilder = SSLContexts.custom();
            try {
                InputStream is = (trustStorePath.startsWith("file:")) ?
                    new FileInputStream(trustStorePath) :
                    ElassandraSecurity.class.getResourceAsStream(trustStorePath);
                KeyStore ks = KeyStore.getInstance(trustStorePath.endsWith("jks") ? "JKS" : "PKCS12");
                ks.load(is, trustStorePass.toCharArray());

                sslBuilder.loadTrustMaterial(ks, new TrustStrategy() {
                    @Override
                    public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        return true;
                    }
                });
                sslContextBuilder.trustManager(is);

                logger.info("Trust store {} sucessfully loaded.", trustStorePath);
            } catch(IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
                logger.error("Failed to load trustStore="+ trustStorePath, e);
            }

            if (keyStorePath != null && keyStorePath.length() > 0) {
                Path keystorePath = get(keyStorePath);
                if (!Files.notExists(keystorePath)) {
                    try {
                        String keyStoreType = keyStorePath.endsWith(".jks") ? "JKS" : "PKCS12";

                        InputStream ksf = Files.newInputStream(keystorePath);
                        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
                        KeyStore ks = KeyStore.getInstance(keyStoreType);
                        ks.load(ksf, keyStorePass.toCharArray());
                        for (Enumeration<String> aliases = ks.aliases(); aliases.hasMoreElements(); ) {
                            String alias = aliases.nextElement();
                            if (ks.getCertificate(alias).getType().equals("X.509")) {
                                Date expires = ((X509Certificate) ks.getCertificate(alias)).getNotAfter();
                                if (expires.before(new Date()))
                                    System.out.println("Certificate for " + alias + " expired on " + expires);
                            }
                        }
                        kmf.init(ks, keyStorePass.toCharArray());
                        sslContextBuilder.keyManager(kmf);
                        sslBuilder.loadKeyMaterial(ks, keyStorePass.toCharArray());
                        logger.info("Keystore {} succefully loaded.", keystorePath);
                    } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException | UnrecoverableKeyException e) {
                        logger.error("Failed to load keystore " + keystorePath, e);
                    }
                }
            }
            try {
                sslContextOption = Optional.of(sslBuilder.build());
                switch(sslProvider){
                    case JDK:
                        sslOptions = Optional.ofNullable(RemoteEndpointAwareJdkSSLOptions.builder()
                            .withSSLContext(sslContextOption.get())
                            .build());
                        break;
                    case OPENSSL:
                        sslOptions = Optional.of(new RemoteEndpointAwareNettySSLOptions(sslContextBuilder.build()));
                }

            } catch (SSLException | NoSuchAlgorithmException | KeyManagementException e) {
                logger.error("Failed to build SSL context", e);
            }
        }
    }

    public Optional<String> getUsername() { return Optional.ofNullable(this.elassandraUsername); }

    public Optional<String> getPassword() { return Optional.ofNullable(this.elassandraPassword); }

    public Optional<SSLOptions> getSSLOptions() {
        return this.sslOptions;
    }

    public Optional<SSLContext> getSSLContextOption() {
        return this.sslContextOption;
    }
}
