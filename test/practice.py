"""A practice database for trying SQL by hand. The tests do not use it.

From the project folder:
    python -m test.practice                          the registrar tables with all their data (Tasks 2 to 6)
    python -m test.practice src/roster.sql           run your file on that data first, then the prompt
    python -m test.practice --empty                  empty tables plus the two import tables (Task 1)
    python -m test.practice --empty src/seed.sql     try your seed script, then the prompt
Each command opens a psql prompt. Leave psql with \\q. The practice server and everything in it are
then deleted, so every start is fresh.
"""

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

from test import support


def psql_command():
    """The psql client that ships with pgserver, or one on PATH."""
    try:
        from pgserver._commands import POSTGRES_BIN_PATH
    except ImportError:
        return shutil.which("psql")
    for name in ("psql", "psql.exe"):
        candidate = Path(POSTGRES_BIN_PATH) / name
        if candidate.exists():
            return str(candidate)
    return shutil.which("psql")


def main(argv) -> int:
    parser = argparse.ArgumentParser(prog="python -m test.practice", description="A practice copy of the registrar database.")
    parser.add_argument("--empty", action="store_true", help="empty tables plus import_students and import_enrollments")
    parser.add_argument("files", nargs="*", help="SQL files to run before the prompt opens")
    args = parser.parse_args(argv)
    psql = psql_command()
    if psql is None:
        print("psql was not found. Run: python -m pip install -r requirements.txt")
        return 1
    server = support.start_server()
    try:
        with support.fresh_database(server) as db:
            if args.empty:
                support.empty_tables(db)
                support.import_tables(db)
            else:
                support.registrar_data(db)
            uri = server.get_uri(database=db.info.dbname)
            for name in args.files:
                print(f"--- running {name}", flush=True)
                subprocess.run([psql, "-X", "-v", "ON_ERROR_STOP=1", "-f", name, uri], check=False)
            print("Practice database ready. Leave psql with \\q to delete it.", flush=True)
            subprocess.run([psql, "-X", uri], check=False)
    finally:
        server.cleanup()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
