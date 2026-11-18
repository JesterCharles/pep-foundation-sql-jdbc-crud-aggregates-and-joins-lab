"""pytest fixtures shared by the visible tests and the held-out tests."""

import pytest

from test import support


@pytest.fixture(scope="session")
def pg_server():
    """One private PostgreSQL for the whole run, deleted at the end."""
    server, problem = None, None
    try:
        server = support.start_server()
    except RuntimeError as error:
        problem = str(error)
    if server is None:
        pytest.fail(problem, pytrace=False)
    yield server
    server.cleanup()


@pytest.fixture
def db(pg_server):
    """A brand-new empty database for one test, with an autocommit connection to it."""
    with support.fresh_database(pg_server) as conn:
        yield conn
