PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS administracio (
    id        INTEGER PRIMARY KEY,
    codi      TEXT NOT NULL UNIQUE,
    nom       TEXT NOT NULL,
    nivell    TEXT NOT NULL CHECK (nivell IN ('local', 'autonomic', 'estatal')),
    comunitat TEXT,
    url       TEXT,
    identificadors TEXT    -- JSON: ine10, dir3, aoc_ambit, ...
);

CREATE TABLE IF NOT EXISTS font (
    id        INTEGER PRIMARY KEY,
    codi      TEXT NOT NULL UNIQUE,
    nom       TEXT NOT NULL,
    url       TEXT,
    llicencia TEXT
);

CREATE TABLE IF NOT EXISTS ingest_run (
    id         INTEGER PRIMARY KEY,
    font       TEXT NOT NULL REFERENCES font(codi),
    inici      TEXT NOT NULL,
    fi         TEXT,
    estat      TEXT NOT NULL DEFAULT 'running' CHECK (estat IN ('running', 'ok', 'error')),
    registres  INTEGER NOT NULL DEFAULT 0,
    missatge   TEXT
);

CREATE TABLE IF NOT EXISTS contracte (
    id                  INTEGER PRIMARY KEY,
    font                TEXT NOT NULL REFERENCES font(codi),
    id_extern           TEXT NOT NULL,
    administracio_id    INTEGER REFERENCES administracio(id),
    organ               TEXT,
    organ_nif           TEXT,
    expedient           TEXT,
    titol               TEXT,
    tipus               TEXT,
    procediment         TEXT,
    estat               TEXT,
    import_licitacio    REAL,
    import_adjudicacio  REAL,
    data_publicacio     TEXT,
    data_adjudicacio    TEXT,
    adjudicatari_nom    TEXT,
    adjudicatari_nif    TEXT,
    num_ofertes         INTEGER,
    cpv                 TEXT,
    url                 TEXT,
    raw                 TEXT,
    actualitzat         TEXT NOT NULL,
    UNIQUE (font, id_extern)
);
CREATE INDEX IF NOT EXISTS idx_contracte_adj ON contracte(adjudicatari_nif);
CREATE INDEX IF NOT EXISTS idx_contracte_data ON contracte(data_adjudicacio);
CREATE INDEX IF NOT EXISTS idx_contracte_admin ON contracte(administracio_id);

CREATE TABLE IF NOT EXISTS subvencio (
    id                INTEGER PRIMARY KEY,
    font              TEXT NOT NULL REFERENCES font(codi),
    id_extern         TEXT NOT NULL,
    administracio_id  INTEGER REFERENCES administracio(id),
    organ             TEXT,
    beneficiari       TEXT,
    beneficiari_nif   TEXT,
    import            REAL,
    data_concessio    TEXT,
    convocatoria_id   TEXT,
    convocatoria      TEXT,
    instrument        TEXT,
    url_bases         TEXT,
    raw               TEXT,
    actualitzat       TEXT NOT NULL,
    UNIQUE (font, id_extern)
);
CREATE INDEX IF NOT EXISTS idx_subvencio_benef ON subvencio(beneficiari_nif);
CREATE INDEX IF NOT EXISTS idx_subvencio_data ON subvencio(data_concessio);

CREATE TABLE IF NOT EXISTS dataset (
    id                INTEGER PRIMARY KEY,
    font              TEXT NOT NULL REFERENCES font(codi),
    id_extern         TEXT NOT NULL,
    administracio_id  INTEGER REFERENCES administracio(id),
    titol             TEXT,
    descripcio        TEXT,
    publicador        TEXT,
    temes             TEXT,
    formats           TEXT,
    url               TEXT,
    data_modificacio  TEXT,
    raw               TEXT,
    actualitzat       TEXT NOT NULL,
    UNIQUE (font, id_extern)
);

CREATE TABLE IF NOT EXISTS indicador (
    id                INTEGER PRIMARY KEY,
    font              TEXT NOT NULL REFERENCES font(codi),
    id_extern         TEXT NOT NULL,
    administracio_id  INTEGER REFERENCES administracio(id),
    seccio            TEXT,
    nom               TEXT,
    valor             TEXT,
    valor_num         REAL,
    unitat            TEXT,
    variacio_anual    TEXT,
    periode           TEXT,
    any               INTEGER,
    font_dada         TEXT,
    descripcio        TEXT,
    url               TEXT,
    raw               TEXT,
    actualitzat       TEXT NOT NULL,
    UNIQUE (font, id_extern)
);
CREATE INDEX IF NOT EXISTS idx_indicador_seccio ON indicador(seccio);

CREATE TABLE IF NOT EXISTS pressupost (
    id                       INTEGER PRIMARY KEY,
    font                     TEXT NOT NULL REFERENCES font(codi),
    id_extern                TEXT NOT NULL,
    administracio_id         INTEGER REFERENCES administracio(id),
    exercici                 TEXT,
    periode                  TEXT,
    capitol                  TEXT,
    capitol_codi             TEXT,
    credit_inicial           REAL,
    pressupost_definitiu     REAL,
    autoritzat               REAL,
    obligacions_reconegudes  REAL,
    obligacions_pagades      REAL,
    url                      TEXT,
    raw                      TEXT,
    actualitzat              TEXT NOT NULL,
    UNIQUE (font, id_extern)
);
CREATE INDEX IF NOT EXISTS idx_pressupost_exercici ON pressupost(exercici);

CREATE TABLE IF NOT EXISTS document (
    id                INTEGER PRIMARY KEY,
    font              TEXT NOT NULL REFERENCES font(codi),
    id_extern         TEXT NOT NULL,
    administracio_id  INTEGER REFERENCES administracio(id),
    titol             TEXT,
    categoria         TEXT,
    url               TEXT NOT NULL,
    tipus_fitxer      TEXT,
    pagina_origen     TEXT,
    raw               TEXT,
    actualitzat       TEXT NOT NULL,
    UNIQUE (font, id_extern)
);

CREATE VIEW IF NOT EXISTS v_contractes_per_any AS
SELECT a.nom AS administracio,
       substr(COALESCE(c.data_adjudicacio, c.data_publicacio), 1, 4) AS any,
       COUNT(*) AS n,
       ROUND(SUM(COALESCE(c.import_adjudicacio, c.import_licitacio)), 2) AS total
FROM contracte c LEFT JOIN administracio a ON a.id = c.administracio_id
GROUP BY 1, 2;

CREATE VIEW IF NOT EXISTS v_top_adjudicataris AS
SELECT a.nom AS administracio,
       COALESCE(c.adjudicatari_nif, c.adjudicatari_nom) AS clau,
       MAX(c.adjudicatari_nom) AS adjudicatari,
       COUNT(*) AS n,
       ROUND(SUM(COALESCE(c.import_adjudicacio, c.import_licitacio)), 2) AS total
FROM contracte c LEFT JOIN administracio a ON a.id = c.administracio_id
WHERE c.adjudicatari_nom IS NOT NULL
GROUP BY 1, 2;

CREATE VIEW IF NOT EXISTS v_pressupost_per_exercici AS
SELECT a.nom AS administracio,
       p.exercici,
       p.capitol,
       ROUND(SUM(p.pressupost_definitiu), 2) AS definitiu,
       ROUND(SUM(p.obligacions_reconegudes), 2) AS reconegudes,
       ROUND(SUM(p.obligacions_pagades), 2) AS pagades
FROM pressupost p LEFT JOIN administracio a ON a.id = p.administracio_id
GROUP BY 1, 2, 3;

CREATE VIEW IF NOT EXISTS v_subvencions_per_any AS
SELECT a.nom AS administracio,
       substr(s.data_concessio, 1, 4) AS any,
       COUNT(*) AS n,
       ROUND(SUM(s.import), 2) AS total
FROM subvencio s LEFT JOIN administracio a ON a.id = s.administracio_id
GROUP BY 1, 2;
