from fairfactory.connectors.aoc import AocContractesConnector
from fairfactory.connectors.asturias import AsturiasConnector
from fairfactory.connectors.base import Connector
from fairfactory.connectors.bdns import BDNSConnector
from fairfactory.connectors.catalunya import CatalunyaSocrataConnector
from fairfactory.connectors.cifras import SieroCifrasConnector
from fairfactory.connectors.datos_gob import DatosGobConnector
from fairfactory.connectors.figueres import (
    FigueresCkanConnector,
    FigueresContractesConnector,
)
from fairfactory.connectors.girona import DiputacioGironaConnector
from fairfactory.connectors.pcsp import PCSPConnector
from fairfactory.connectors.sede import SedeSieroConnector
from fairfactory.connectors.siero import SieroConnector

CONNECTORS: dict[str, type[Connector]] = {
    c.codi: c
    for c in (
        BDNSConnector,
        DatosGobConnector,
        PCSPConnector,
        SieroConnector,
        AsturiasConnector,
        SedeSieroConnector,
        SieroCifrasConnector,
        FigueresCkanConnector,
        FigueresContractesConnector,
        DiputacioGironaConnector,
        CatalunyaSocrataConnector,
        AocContractesConnector,
    )
}

__all__ = ["CONNECTORS", "Connector"]
