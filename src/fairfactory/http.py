from __future__ import annotations

import hashlib
import logging
import time
from pathlib import Path

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

log = logging.getLogger(__name__)

USER_AGENT = (
    "fair-factory/0.1 (+https://github.com/giro-dev/fair-factory; auditoria ciutadana, "
    "peticions espaiades)"
)
CACHE_DIR = Path("data/cache")


class Client:
    """HTTP client with retries, a polite delay between requests and an on-disk cache."""

    def __init__(
        self, delay: float = 1.0, timeout: float = 60.0, cache_dir: Path | None = CACHE_DIR
    ):
        self.delay = delay
        self.timeout = timeout
        self.cache_dir = cache_dir
        self._last = 0.0
        self.session = requests.Session()
        self.session.headers["User-Agent"] = USER_AGENT
        retry = Retry(
            total=4,
            backoff_factor=2,
            status_forcelist=(429, 500, 502, 503, 504),
            allowed_methods=("GET",),
        )
        self.session.mount("https://", HTTPAdapter(max_retries=retry))
        self.session.mount("http://", HTTPAdapter(max_retries=retry))

    def _wait(self) -> None:
        elapsed = time.monotonic() - self._last
        if elapsed < self.delay:
            time.sleep(self.delay - elapsed)
        self._last = time.monotonic()

    def get(
        self,
        url: str,
        *,
        params: dict | None = None,
        headers: dict | None = None,
        cache: bool = False,
    ) -> requests.Response:
        key = None
        if cache and self.cache_dir is not None:
            req = requests.Request("GET", url, params=params).prepare()
            key = self.cache_dir / hashlib.sha256(req.url.encode()).hexdigest()
            if key.exists():
                log.debug("cache hit %s", req.url)
                resp = requests.Response()
                resp.status_code = 200
                resp._content = key.read_bytes()
                resp.url = req.url
                return resp
        self._wait()
        log.info("GET %s", url)
        resp = self.session.get(url, params=params, headers=headers, timeout=self.timeout)
        resp.raise_for_status()
        if key is not None:
            key.parent.mkdir(parents=True, exist_ok=True)
            key.write_bytes(resp.content)
        return resp

    def get_json(self, url: str, **kwargs):
        headers = {"Accept": "application/json", **kwargs.pop("headers", {})}
        return self.get(url, headers=headers, **kwargs).json()
