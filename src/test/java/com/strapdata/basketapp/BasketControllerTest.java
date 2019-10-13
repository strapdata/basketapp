package com.strapdata.basketapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.strapdata.basketapp.controllers.BasketController;
import com.strapdata.basketapp.model.Basket;
import com.strapdata.basketapp.model.BasketItem;
import com.strapdata.basketapp.model.BasketStatus;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.annotation.MicronautTest;
import org.cassandraunit.ElassandraCQLUnit5;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import javax.inject.Inject;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@ExtendWith(ElassandraCQLUnit5.class)
@ExtendWith(BasketControllerTest.class)
public class BasketControllerTest implements BeforeEachCallback, AfterEachCallback {

    public static final Basket DEMO_BASKET1 = new Basket()
        .withId(UUID.randomUUID())
        .withBasketStatus(BasketStatus.Finished)
        .withProcessingDate(new Date())
        .withStoreCode("1")
        .withItems(Lists.newArrayList(
            new BasketItem().withProductCode("1").withAmountPaid(1.0).withProductQuantity(1),
            new BasketItem().withProductCode("2").withAmountPaid(2.0).withProductQuantity(2),
            new BasketItem().withProductCode("3").withAmountPaid(3.0).withProductQuantity(3)
        ));

    public static final Basket DEMO_BASKET2 = new Basket()
        .withId(UUID.randomUUID())
        .withBasketStatus(BasketStatus.Finished)
        .withProcessingDate(new Date())
        .withStoreCode("1")
        .withItems(Lists.newArrayList(
            new BasketItem().withProductCode("1").withAmountPaid(1.0).withProductQuantity(1)
        ));

    private static EmbeddedServer server;
    private static ElassandraStorage storage;

    @Inject
    ObjectMapper mapper;

    @BeforeAll
    public static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class);
        storage = server.getApplicationContext().findBean(ElassandraStorage.class).get();
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        storage.open();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        storage.cleanup();
    }

    @Test
    public void testJsonSerialization() throws IOException {
        StringWriter writer = new StringWriter();
        mapper.writeValue(writer, DEMO_BASKET1);
        writer.close();

        String json = writer.getBuffer().toString();
        Basket basket = mapper.readValue(json, Basket.class);
        assertEquals(basket, DEMO_BASKET1);
    }

    @Test
    public void testIndex() throws Exception {
        try(RxHttpClient client = server.getApplicationContext().createBean(RxHttpClient.class, server.getURL())) {
            assertEquals(HttpStatus.OK, client.toBlocking().exchange(HttpRequest.GET("/basketapp/basket/")).status());
        }
    }

    @Test
    public void testElassandraStorage() {
        storage.getMapper(Basket.class).save(DEMO_BASKET1);
        storage.getMapper(Basket.class).save(DEMO_BASKET2);

        BasketController controller = server.getApplicationContext().getBean(BasketController.class);
        Basket basket = controller.getById(DEMO_BASKET1.getId()).blockingGet();
        assertEquals(DEMO_BASKET1, basket);

        List<Basket> basketWithProduct1 = controller.getByStoreAndProduct(null,"1").blockingGet();
        assertEquals(2, basketWithProduct1.size());
        assertTrue( basketWithProduct1.contains(DEMO_BASKET1));
        assertTrue( basketWithProduct1.contains(DEMO_BASKET2));

        List<Basket> basketWithProduct2 = controller.getByStoreAndProduct("1","2").blockingGet();
        assertEquals(1, basketWithProduct2.size());
        assertTrue( basketWithProduct2.contains(DEMO_BASKET1));
    }

}
