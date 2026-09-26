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
        if name == "bdns":
            if args.since:
                kwargs["since"] = args.since
            if args.bdns_admin:
                kwargs["admins"] = [a.strip() for a in args.bdns_admin.split(",") if a.strip()]
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


def cmd_export(args) -> int:
    """Write a small JSON summary next to the DB (used by the static site)."""
    conn = connect(args.db)
    init_db(conn)
    out = {
        "generat": conn.execute("SELECT MAX(fi) FROM ingest_run").fetchone()[0],
        "taules": {
            t: conn.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0]
            for t in ("contracte", "subvencio", "dataset", "indicador", "pressupost", "document")
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
        "--bdns-admin",
        metavar="ADMINS",
        help="BDNS: només aquestes administracions, separades per comes (per a jobs separats)",
    )
    ing.add_argument(
        "--keep-going", action="store_true", help="surt amb codi 0 encara que alguna font falli"
    )
    ing.set_defaults(func=cmd_ingest)

    sub.add_parser("stats", help="recomptes i darreres ingestes").set_defaults(func=cmd_stats)

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
