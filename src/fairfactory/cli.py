from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path

from fairfactory.connectors import CONNECTORS
from fairfactory.db import DEFAULT_DB, connect, init_db
from fairfactory.http import Client

log = logging.getLogger("fairfactory")


def cmd_init(args) -> int:
    conn = connect(args.db)
    init_db(conn)
    print(f"Base de dades inicialitzada a {args.db}")
    return 0


def cmd_ingest(args) -> int:
    conn = connect(args.db)
    init_db(conn)
    client = Client(delay=args.delay)
    names = args.sources or list(CONNECTORS)
    failed = []
    for name in names:
        cls = CONNECTORS[name]
        kwargs = {}
        if name == "pcsp":
            kwargs["max_pages"] = args.pcsp_pages
        if name == "bdns" and args.since:
            kwargs["since"] = args.since
        connector = cls(conn, client, limit=args.limit, **kwargs)
        try:
            connector.run()
        except Exception:  # noqa: BLE001 - one failing source must not abort the rest
            log.exception("%s: error d'ingesta", name)
            failed.append(name)
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


def cmd_export(args) -> int:
    """Write a small JSON summary next to the DB (used by the static site)."""
    conn = connect(args.db)
    init_db(conn)
    out = {
        "generat": conn.execute("SELECT MAX(fi) FROM ingest_run").fetchone()[0],
        "taules": {
            t: conn.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0]
            for t in ("contracte", "subvencio", "dataset", "document")
        },
        "fonts": [dict(r) for r in conn.execute("SELECT codi, nom, url, llicencia FROM font")],
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
        "--pcsp-pages",
        type=int,
        default=3,
        help="pàgines del feed ATOM de la PCSP a recórrer (~17 MB cadascuna)",
    )
    ing.add_argument("--since", help="BDNS: només concessions des de dd/mm/aaaa")
    ing.add_argument(
        "--keep-going", action="store_true", help="surt amb codi 0 encara que alguna font falli"
    )
    ing.set_defaults(func=cmd_ingest)

    sub.add_parser("stats", help="recomptes i darreres ingestes").set_defaults(func=cmd_stats)

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
