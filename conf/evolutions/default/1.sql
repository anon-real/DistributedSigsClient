-- !Ups
CREATE TABLE Secret (
    a VARCHAR(1000) NOT NULL,
    r VARCHAR(1000) NOT NULL,
    request_id BIGINT(20) NOT NULL,
    PRIMARY KEY (a)
);

CREATE TABLE TX (
    request_id BIGINT(20) NOT NULL,
    tx_bytes BLOB NOT NULL,
    PRIMARY KEY (request_id)
);

-- !Downs
DROP TABLE Secret;
DROP TABLE TX;
