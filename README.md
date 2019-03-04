# Elassandra Junit5 tests with Micronaut

Micronaut is a new JVM-based framework designed to make creating microservices quick and easy. One of the most exciting
features of Micronaut is its support for reactive programming and we will see in this article how to store and search
data in Elassandra in a reactive way through the CQL driver and run unit tests with Junit5.

To illustrate 

# Cassandra object mapping

In order to handle conversion between Cassandra types and custom Java object and generate CQL queries,
the Cassandra mapper provided with the java Cassandra driver. By combining Jackson, Lombok and Cassandra java annotations in 
the Basket POJO object, we get both JSON serialization and Cassandra storage.

```java
@Table(name = "baskets",
    readConsistency = "LOCAL_ONE",
    writeConsistency = "LOCAL_ONE",
    caseSensitiveKeyspace = false,
    caseSensitiveTable = false)
@Data
@Builder
@Wither
@ToString(includeFieldNames=true)
@AllArgsConstructor
@NoArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Basket {
    @PartitionKey(0)
    UUID id;

    @Column(name = "store_code")
    @JsonProperty("store_code")
    String storeCode;

    @Column(name = "basket_status")
    @JsonProperty("basket_status")
    BasketStatus basketStatus;

    @Column(name = "processing_date")
    @JsonProperty("processing_date")
    Date processingDate;

    List<BasketItem> items;
}
```

Thes *BasketItem* is mapped to a Cassandra User Defined Type as follow:
 
````java
@UDT(name="basket_item")
@Data
@Builder
@Wither
@ToString(includeFieldNames=true)
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BasketItem {

    @Field(name = "product_qty")
    @JsonProperty("product_qty")
    private Integer productQuantity;

    @Field(name = "amount_paid")
    @JsonProperty("amount_paid")
    private Double amountPaid;

    @Field(name = "product_code")
    @JsonProperty("product_code")
    private String productCode;
}
````

Finally, **BascketStatus**, a java enum, is managed through a registred Codec as described in the 
[driver documentation](https://docs.datastax.com/en/developer/java-driver/3.5/manual/custom_codecs/extras/#Enums) 

Unfortunately, the java Cassandra mapper cannot generate the CQL schema, so we need to manage it manually:

```bash
CREATE TYPE IF NOT EXISTS basket_item (
    product_code  text,
    product_qty   int,
    amount_paid   double
);

CREATE TABLE IF NOT EXISTS baskets (
    id              uuid PRIMARY KEY,
    store_code      text,
    basket_status   text,
    processing_date timestamp,
    total_paid      double,
    items           list<frozen<basket_item>>,

    es_query text,
    es_options text
);
```

# Elasticsearch query over CQL

Elassandra closely integrates the Elasticsearch code and since version 6.2.3.11+ provides supports for Elasticsearch query 
over the Cassandra driver is opensource, meaning that you can query Elasticsearch through the various CQL driver implementations.

Dummy Cassandra columns **es_query** and **es_options** allow to send elasticsearch search request to the Elassandra coordinator nodes 
and retreive results as a Cassandra rows, and the Cassandra [Accessors](https://docs.datastax.com/en/developer/java-driver/3.5/manual/object_mapper/using/#Accessors)
annotation provides a nice way to map such custom queries. 

Then a static helper method based on the elasticsearch REST client API provides an easy way to build Elasticsearch queries.

```java
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
```

# Miconaut Reactive Data Access

Micronaut use the RxJava2 as the as the implementation for the non-blocking I/O Reactive Streams API by default, and as said in the 
documentation, if your controller method returns a non-blocking type then Micronaut will use the Event loop thread 
to subscribe to the result. 

In our Micronaut basket controller, ListenableFuture returned by the Cassandra driver is converted to reactive types such as Single or Observable.

```java
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
```

The TransformedListenableFuture wraps the ListenableFuture<Result<X>> to convert the result by applying a mapper function, here Result::all. 
 
# Junit5 Elassandra Tests

The [Micronaut Testing Framework extensions](https://micronaut-projects.github.io/micronaut-test/latest/guide/index.html#introduction) 
included support for [JUnit 5](https://junit.org/junit5/), the next generation of JUnit.

Nevertheless, in order to use the [Elassandra-Unit](https://github.com/strapdata/elassandra-unit) based on Junit 4, 
we need to implements some Junit 5 extensions to trigger before and after test operations. First, an ElassandraCQLUnit5 extension
to start an embedded Elassandra node where we set the system property **cassandra.custom_query_handler_class** to 
to enable support for Elasticsearch query over CQL.

```Java
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
```

Then, our **BasketControllerTest** also implements a JUnit5 extension to open and cleanup Elassandra node before and 
after each tests. The **testElassandraStorage** test our elasticsearch nested query on baskets.

```Java
@MicronautTest
@ExtendWith(ElassandraCQLUnit5.class)
@ExtendWith(BasketControllerTest.class)
public class BasketControllerTest implements BeforeEachCallback, AfterEachCallback {

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
    
    ...
}```
                                                         


# Kuberentes deployment

