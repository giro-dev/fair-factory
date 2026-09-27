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

CREATE TABLE IF NOT EXISTS administracio_partit (
    id                INTEGER PRIMARY KEY,
    font              TEXT NOT NULL REFERENCES font(codi),
    id_extern         TEXT NOT NULL,
    administracio_id  INTEGER NOT NULL REFERENCES administracio(id),
    partit_codi       TEXT,
    partit_nom        TEXT,
    responsable       TEXT,          -- alcalde/essa o president/a
    data_inici        TEXT,          -- inici del mandat
    data_fi           TEXT,          -- NULL = mandat vigent
    confianca         TEXT CHECK (confianca IN ('alta', 'mitjana', 'baixa')),
    url               TEXT,
    raw               TEXT,
    actualitzat       TEXT NOT NULL,
    UNIQUE (font, id_extern)
);
CREATE INDEX IF NOT EXISTS idx_adminpartit_admin ON administracio_partit(administracio_id);
CREATE INDEX IF NOT EXISTS idx_adminpartit_dates ON administracio_partit(data_inici, data_fi);

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

-- Flux públic total cap a un mateix NIF: contractes + subvencions
CREATE VIEW IF NOT EXISTS v_flux_public_per_nif AS
SELECT administracio, nif, nom,
       SUM(n_contractes)   AS n_contractes,
       SUM(import_contractes)   AS import_contractes,
       SUM(n_subvencions)  AS n_subvencions,
       SUM(import_subvencions)  AS import_subvencions,
       SUM(import_contractes) + SUM(import_subvencions) AS total
FROM (
    SELECT a.nom AS administracio, c.adjudicatari_nif AS nif, c.adjudicatari_nom AS nom,
           COUNT(*) AS n_contractes, SUM(c.import_adjudicacio) AS import_contractes,
           0 AS n_subvencions, 0 AS import_subvencions
    FROM contracte c JOIN administracio a ON a.id = c.administracio_id
    WHERE c.adjudicatari_nif IS NOT NULL AND c.adjudicatari_nif != ''
    GROUP BY a.nom, c.adjudicatari_nif
    UNION ALL
    SELECT a.nom, s.beneficiari_nif, s.beneficiari,
           0, 0, COUNT(*), SUM(s.import)
    FROM subvencio s JOIN administracio a ON a.id = s.administracio_id
    WHERE s.beneficiari_nif IS NOT NULL AND s.beneficiari_nif != ''
    GROUP BY a.nom, s.beneficiari_nif
)
GROUP BY administracio, nif
ORDER BY total DESC;

-- Detecció de fraccionament de contracte: diversos contractes del mateix
-- adjudicatari al mateix òrgan en un trimestre, amb total elevat
CREATE VIEW IF NOT EXISTS v_fraccionament_sospitos AS
SELECT a.nom AS administracio,
       c.organ,
       c.adjudicatari_nom,
       c.adjudicatari_nif,
       substr(c.data_adjudicacio, 1, 4) AS any,
       (CAST(substr(c.data_adjudicacio, 6, 2) AS INTEGER) + 2) / 3 AS trimestre,
       COUNT(*) AS n_contractes,
       ROUND(SUM(c.import_adjudicacio), 2) AS total
FROM contracte c JOIN administracio a ON a.id = c.administracio_id
WHERE c.adjudicatari_nif IS NOT NULL
  AND c.data_adjudicacio IS NOT NULL
  AND c.import_adjudicacio IS NOT NULL
GROUP BY a.nom, c.organ, c.adjudicatari_nif, any, trimestre
HAVING n_contractes >= 3 AND total >= 15000
ORDER BY total DESC;

CREATE VIEW IF NOT EXISTS v_subvencions_per_any AS
SELECT a.nom AS administracio,
       substr(s.data_concessio, 1, 4) AS any,
       COUNT(*) AS n,
       ROUND(SUM(s.import), 2) AS total
FROM subvencio s LEFT JOIN administracio a ON a.id = s.administracio_id
GROUP BY 1, 2;

-- Contractes agrupats pel partit del cap de l'administració en la data del
-- contracte (adjudicació o publicació). Els contractes sense mandat conegut
-- o sense data queden a 'sense dada'.
CREATE VIEW IF NOT EXISTS v_contractes_per_partit AS
SELECT a.nom AS administracio,
       COALESCE(p.partit_codi, 'sense dada') AS partit,
       substr(COALESCE(c.data_adjudicacio, c.data_publicacio), 1, 4) AS any,
       COUNT(*) AS n,
       ROUND(SUM(COALESCE(c.import_adjudicacio, c.import_licitacio)), 2) AS total
FROM contracte c
JOIN administracio a ON a.id = c.administracio_id
LEFT JOIN administracio_partit p
       ON p.administracio_id = c.administracio_id
      AND COALESCE(c.data_adjudicacio, c.data_publicacio) >= p.data_inici
      AND (p.data_fi IS NULL
           OR COALESCE(c.data_adjudicacio, c.data_publicacio) <= p.data_fi)
GROUP BY 1, 2, 3;
