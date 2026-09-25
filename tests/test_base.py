from fairfactory.connectors.base import parse_es_date, parse_es_number, split_nif


def test_parse_es_number():
    assert parse_es_number("2.626,30") == 2626.30
    assert parse_es_number("-15,00") == -15.0
    assert parse_es_number("1234.5") == 1234.5
    assert parse_es_number("") is None
    assert parse_es_number(None) is None


def test_parse_es_date():
    assert parse_es_date("17-02-2022") == "2022-02-17"
    assert parse_es_date("17/02/2022") == "2022-02-17"
    assert parse_es_date("no") is None


def test_split_nif():
    assert split_nif("B12345678 EMPRESA SL") == ("B12345678", "EMPRESA SL")
    assert split_nif("EMPRESA SENSE NIF") == (None, "EMPRESA SENSE NIF")
    assert split_nif(None) == (None, None)
