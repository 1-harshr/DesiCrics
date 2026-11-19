-- Player auctions: one per tournament, its player pool, and the append-only log its state is folded from.
CREATE TABLE auctions (
    id            VARCHAR(36)  NOT NULL,
    tournament_id VARCHAR(36)  NOT NULL,
    -- JSON AuctionSettings.
    settings      TEXT         NOT NULL,
    -- Kept in step with the events, so live auctions can be found without folding every log.
    status        VARCHAR(10)  NOT NULL,
    created_by    VARCHAR(128) NOT NULL,
    created_at    BIGINT       NOT NULL,
    CONSTRAINT pk_auctions PRIMARY KEY (id),
    CONSTRAINT uq_auctions_tournament UNIQUE (tournament_id),
    CONSTRAINT fk_auctions_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments (id) ON DELETE CASCADE
);

CREATE TABLE auction_players (
    auction_id     VARCHAR(36)  NOT NULL,
    player_id      VARCHAR(128) NOT NULL,
    base_price     INT          NOT NULL,
    category       VARCHAR(30),
    -- Asked to join from the tournament page; waits for an organiser.
    requested      BOOLEAN      NOT NULL DEFAULT FALSE,
    retained_by    VARCHAR(36),
    retained_price INT,
    added_at       BIGINT       NOT NULL,
    CONSTRAINT pk_auction_players PRIMARY KEY (auction_id, player_id),
    CONSTRAINT fk_auction_players_auction FOREIGN KEY (auction_id) REFERENCES auctions (id) ON DELETE CASCADE,
    CONSTRAINT fk_auction_players_player FOREIGN KEY (player_id) REFERENCES players (id)
);
CREATE INDEX auction_players_player ON auction_players (player_id);

CREATE TABLE auction_events (
    auction_id VARCHAR(36)  NOT NULL,
    seq        INT          NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    -- JSON AuctionEvent.
    data       TEXT         NOT NULL,
    created_by VARCHAR(128),
    created_at BIGINT       NOT NULL,
    CONSTRAINT pk_auction_events PRIMARY KEY (auction_id, seq),
    CONSTRAINT fk_auction_events_auction FOREIGN KEY (auction_id) REFERENCES auctions (id) ON DELETE CASCADE
);
