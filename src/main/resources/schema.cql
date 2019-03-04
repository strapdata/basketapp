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
