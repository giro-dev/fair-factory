from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path

from fairfactory.connectors import CONNECTORS
from fairfactory.db import DEFAULT_DB, connect, init_db, merge_into
from fairfactory.http import Client

log = logging.getLogger("fairfactory")


def cmd_init(args) -> int:
    conn = connect(args.db)
    init_db(conn)
    print(f"Base de dades inicialitzada a {args.db}")
    return 0


def _connector_kwargs(cls, args) -> dict:
    """Tradueix les cli_options declarades pel connector a kwargs d'__init__."""
    kwargs = {}
    for flag, _add_kwargs, param in cls.cli_options:
        val = getattr(args, flag.lstrip("-").replace("-", "_"), None)
        if val is not None:
            kwargs[param] = val
    return kwargs


def _run_connector(args, name: str) -> tuple[str, bool]:
    """Executa un connector amb connexió pròpia (permet paral·lelisme)."""
    conn = connect(args.db)
    try:
        init_db(conn)
        cls = CONNECTORS[name]
        cls(conn, Client(delay=args.delay), limit=args.limit, **_connector_kwargs(cls, args)).run()
        return name, True
    except Exception:  # noqa: BLE001 - one failing source must not abort the rest
        log.exception("%s: error d'ingesta", name)
        return name, False
    finally:
        conn.close()


def cmd_ingest(args) -> int:
    names = args.sources or list(CONNECTORS)
    if str(args.db) == ":memory:" and args.jobs > 1:
        log.warning("--jobs>1 no funciona amb :memory: (cada connexió és una BD pròpia)")
        args.jobs = 1
    if args.jobs > 1:
        # cada connector és I/O-bound (espera HTTP); un fil per connector
        from concurrent.futures import ThreadPoolExecutor

        with ThreadPoolExecutor(max_workers=min(args.jobs, len(names))) as pool:
            results = list(pool.map(lambda n: _run_connector(args, n), names))
        failed = [n for n, ok in results if not ok]
    else:
        failed = [n for n in names if not _run_connector(args, n)[1]]
    conn = connect(args.db)
    conn.execute("VACUUM")
    conn.close()
    if failed:
        print(f"Fonts amb error: {', '.join(failed)}", file=sys.stderr)
        return 0 if args.keep_going else 1
    return 0


def cmd_stats(args) -> int:
    conn = connect(args.db)
    init_db(conn)
    stats = {}
    for table in (
        "administracio",
        "font",
        "ingest_run",
        "contracte",
        "subvencio",
        "dataset",
        "indicador",
        "pressupost",
        "document",
    ):
        stats[table] = conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
    runs = [
        dict(r)
        for r in conn.execute(
            "SELECT font, inici, fi, estat, registres, missatge FROM ingest_run "
            "ORDER BY id DESC LIMIT 20"
        )
    ]
    print(json.dumps({"taules": stats, "darreres_ingestes": runs}, ensure_ascii=False, indent=2))
    return 0


def cmd_merge(args) -> int:
    """Merge partial ingest DBs (per-admin jobs) into a single DB."""
    out = Path(args.out)
    if out.exists() and not args.keep:
        out.unlink()
    conn = connect(out)
    init_db(conn)
    for src in args.sources:
        log.info("fusionant %s", src)
        merge_into(conn, src)
    conn.execute("VACUUM")
    conn.close()
    print(f"Base de dades fusionada a {out}")
    return 0


def cmd_slim(args) -> int:
    """Strip bulky `raw` payloads so the published DB stays small.

    `raw` duplicates fields already normalized into columns; the canonical
    runner DB keeps it — this only slims the artifact shipped to the web.
    """
    conn = connect(args.db)
    n = conn.execute("UPDATE subvencio SET raw = NULL WHERE raw IS NOT NULL").rowcount
    conn.commit()
    conn.execute("VACUUM")
    conn.close()
    print(f"subvencio.raw buidat en {n} registres")
    return 0


def cmd_groups(args) -> int:
    """Show the connector -> workflow-group mapping (ingest matrix)."""
    for cls in CONNECTORS.values():
        print(f"{cls.codi:25} grups={','.join(cls.grups):25} taules={','.join(cls.taules)}")
    return 0


def cmd_export(args) -> int:
    """Write a small JSON summary next to the DB (used by the static site)."""
    from fairfactory.db import DATA_TABLES

    conn = connect(args.db)
    init_db(conn)
    admin_names = {r["id"]: r["nom"] for r in conn.execute("SELECT id, nom FROM administracio")}
    fonts = []
    for r in conn.execute("SELECT codi, nom, url, llicencia FROM font ORDER BY codi"):
        f = dict(r)
        f["taules"] = set()
        f["administracions"] = set()
        total = 0
        for t in DATA_TABLES:
            rows = conn.execute(
                f"SELECT administracio_id, COUNT(*) FROM {t} WHERE font = ? "
                "GROUP BY administracio_id",
                (f["codi"],),
            ).fetchall()
            if rows:
                f["taules"].add(t)
            for aid, cnt in rows:
                f["administracions"].add(aid)
                total += cnt
        f["taules"] = sorted(f["taules"])
        f["administracions"] = sorted(
            admin_names[a] for a in f["administracions"] if a in admin_names
        )
        f["total_registres"] = total
        run = conn.execute(
            "SELECT inici, fi, estat, registres, missatge FROM ingest_run "
            "WHERE font = ? ORDER BY id DESC LIMIT 1",
            (f["codi"],),
        ).fetchone()
        f["darrera_ingesta"] = dict(run) if run else None
        fonts.append(f)
    out = {
        "generat": conn.execute("SELECT MAX(fi) FROM ingest_run").fetchone()[0],
        "taules": {t: conn.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0] for t in DATA_TABLES},
        "administracions": [
            dict(r)
            for r in conn.execute(
                "SELECT codi, nom, nivell, comunitat, url FROM administracio ORDER BY comunitat"
            )
        ],
        "fonts": fonts,
        "ingestes": [
            dict(r)
            for r in conn.execute(
                "SELECT font, inici, fi, estat, registres, missatge FROM ingest_run "
                "ORDER BY id DESC LIMIT 50"
            )
        ],
    }
    Path(args.out).write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Resum escrit a {args.out}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="fair-factory", description=__doc__)
    p.add_argument("--db", default=str(DEFAULT_DB), help="camí de la base de dades SQLite")
    p.add_argument("-v", "--verbose", action="store_true")
    sub = p.add_subparsers(dest="cmd", required=True)

    sub.add_parser("init", help="crea l'esquema").set_defaults(func=cmd_init)

    ing = sub.add_parser("ingest", help="executa els connectors")
    ing.add_argument(
        "sources",
        nargs="*",
        choices=[*CONNECTORS, []],
        metavar="FONT",
        help=f"fonts a ingerir ({', '.join(CONNECTORS)}); per defecte totes",
    )
    ing.add_argument("--limit", type=int, help="màxim de registres per font (proves)")
    ing.add_argument("--delay", type=float, default=1.0, help="segons entre peticions")
    ing.add_argument(
        "--jobs",
        type=int,
        default=1,
        metavar="N",
        help="connectors en paral·lel (un fil cadascun, connexió SQLite pròpia)",
    )
    # opcions específiques de cada connector, declarades a `cli_options`
    for cls in CONNECTORS.values():
        for flag, add_kwargs, _param in cls.cli_options:
            ing.add_argument(flag, **add_kwargs)
    ing.add_argument(
        "--keep-going", action="store_true", help="surt amb codi 0 encara que alguna font falli"
    )
    ing.set_defaults(func=cmd_ingest)

    sub.add_parser("stats", help="recomptes i darreres ingestes").set_defaults(func=cmd_stats)
    sub.add_parser("groups", help="connector -> grup del workflow i taules on escriu").set_defaults(
        func=cmd_groups
    )

    mer = sub.add_parser("merge", help="fusiona BDs parcials (jobs per administració)")
    mer.add_argument("sources", nargs="+", metavar="SQLITE", help="BDs parcials a fusionar")
    mer.add_argument("--out", required=True, help="BD de destí")
    mer.add_argument(
        "--keep", action="store_true", help="conserva la BD de destí existent (fusiona a sobre)"
    )
    mer.set_defaults(func=cmd_merge)

    sub.add_parser(
        "slim", help="buida subvencio.raw (artefacte de publicació més lleuger)"
    ).set_defaults(func=cmd_slim)

    exp = sub.add_parser("export-summary", help="escriu un resum JSON per al web estàtic")
    exp.add_argument("--out", default="data/summary.json")
    exp.set_defaults(func=cmd_export)
    return p


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    logging.getLogger("pdfminer").setLevel(logging.WARNING)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
