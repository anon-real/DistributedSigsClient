-- !Ups
CREATE TABLE Secret (
    a VARCHAR(1000) NOT NULL,
    r VARCHAR(1000) NOT NULL,
    request_id BIGINT(20) NOT NULL,
    PRIMARY KEY (a)
);

-- !Downs
DROP TABLE Secret;
