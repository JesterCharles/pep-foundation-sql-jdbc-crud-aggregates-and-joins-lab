# CRUD, Aggregates and Joins — Code lab

Run a registrar's term desk: load data, make corrections with UPDATE/DELETE ... RETURNING, and write four reports with joins, CASE, GROUP BY/HAVING and NOT EXISTS.

- **Edit:** the `.sql` files in `src/`, one per task. Each task is marked `TODO`.
- **Run the tests:** **Run Tests** in Exa Studio, or `pip install -r requirements.txt` then `python -m pytest -q`. All 6 visible tests fail at first; every message starts with `Task N:`.
- **No database to install:** the tests start a private PostgreSQL 16 (via `pgserver`, Python 3.9-3.12) and delete it afterwards. `python -m test.practice src/<file>.sql` opens a `psql` prompt to try queries by hand.
- **Done** when every visible test passes. **Submit** also runs held-out tests you can't see.

Full task steps and hints: `README.md` in the project folder.
