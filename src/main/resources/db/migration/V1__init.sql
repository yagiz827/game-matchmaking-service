CREATE TABLE players
(
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(50) NOT NULL UNIQUE,
    country    VARCHAR(2)  NOT NULL,
    level      INT         NOT NULL,
    coins      BIGINT      NOT NULL CHECK (coins >= 0),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE tournaments
(
    id         BIGSERIAL PRIMARY KEY,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE matches
(
    id            BIGSERIAL PRIMARY KEY,
    tournament_id BIGINT      NOT NULL REFERENCES tournaments (id),
    status        VARCHAR(20) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    completed_at  TIMESTAMPTZ
);

CREATE INDEX idx_matches_status_created ON matches (status, created_at);

-- One row per player per tournament; the unique constraint blocks double entry.
CREATE TABLE tournament_entries
(
    id            BIGSERIAL PRIMARY KEY,
    tournament_id BIGINT      NOT NULL REFERENCES tournaments (id),
    player_id     BIGINT      NOT NULL REFERENCES players (id),
    country       VARCHAR(2)  NOT NULL,
    status        VARCHAR(20) NOT NULL,
    score         INT         NOT NULL DEFAULT 0,
    match_id      BIGINT REFERENCES matches (id),
    joined_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_entry_tournament_player UNIQUE (tournament_id, player_id)
);

CREATE INDEX idx_entries_status_joined ON tournament_entries (status, joined_at);

CREATE TABLE match_participants
(
    id            BIGSERIAL PRIMARY KEY,
    match_id      BIGINT     NOT NULL REFERENCES matches (id),
    player_id     BIGINT     NOT NULL REFERENCES players (id),
    country       VARCHAR(2) NOT NULL,
    placement     INT,
    points        INT,
    coins_awarded BIGINT,
    CONSTRAINT uq_participant_match_player UNIQUE (match_id, player_id)
);
