"""Issue #12: contracte mínim del Connector + wiring genèric de la CLI."""

import pytest

from fairfactory.cli import build_parser
from fairfactory.connectors import CONNECTORS
from fairfactory.connectors.bdns import BDNSConnector
from fairfactory.db import connect, init_db


@pytest.fixture
def conn():
    c = connect(":memory:")
    init_db(c)
    return c


def test_connector_metadata():
    for cls in CONNECTORS.values():
        assert cls.codi and cls.nom and cls.url
        assert cls.grups  # job del workflow on s'executa
        assert cls.taules  # taules on escriu
        for g in cls.grups:
            assert g in ("asturias", "catalunya", "compartit")


def test_cli_options_registrades():
    """Les cli_options de cada connector es registren al parser sense
    lògica específica a cli.py."""
    p = build_parser()
    args = p.parse_args(
        [
            "ingest",
            "--since",
            "01/01/2024",
            "--bdns-admin",
            "siero,asturias",
            "--pcsp-pages",
            "5",
            "bdns",
        ]
    )
    assert args.since == "01/01/2024"
    assert args.bdns_admin == "siero,asturias"
    assert args.pcsp_pages == 5
    assert args.sources == ["bdns"]


def test_kwargs_generics(conn):
    """cmd_ingest passa els valors via cli_options sense saber el connector."""
    seen = {}

    class Probe(BDNSConnector):
        def __init__(self, *a, **kw):
            super().__init__(*a, **kw)
            seen["since"], seen["admins"] = self.since, self.admins

    p = build_parser()
    args = p.parse_args(["ingest", "--bdns-admin", "siero,asturias", "bdns"])
    # mateix bucle genèric que cmd_ingest
    kwargs = {
        param: getattr(args, flag.lstrip("-").replace("-", "_"))
        for flag, _kw, param in Probe.cli_options
        if getattr(args, flag.lstrip("-").replace("-", "_"), None) is not None
    }
    Probe(conn, **kwargs)
    assert seen["admins"] == ["siero", "asturias"]
