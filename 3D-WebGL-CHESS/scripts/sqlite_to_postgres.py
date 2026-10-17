#!/usr/bin/env python3
"""Copies the data from the old SQLite chess.db files into the PostgreSQL database.

Prints one SQL script (a single transaction) for psql, so no PostgreSQL driver is needed:

    python3 scripts/sqlite_to_postgres.py chess.db [more.db ...] | psql -v ON_ERROR_STOP=1 -U chess -d chess

Start the app against PostgreSQL once first, so Liquibase has created the (empty) tables. The
Liquibase history itself isn't copied: PostgreSQL has its own. Several files are merged; ids that
clash between them make the transaction fail rather than overwrite anything.
"""
import sqlite3
import sys

# in dependency order: accounts before the games that reference them, sessions before their attributes
TABLES = ["user_account", "game", "spring_session", "spring_session_attributes"]
# SQLite kept these as INTEGER epoch milliseconds (UTC); PostgreSQL has real timestamps
TIMESTAMPS = {"created_at", "last_login_at", "updated_at"}
BOOLEANS = {"rated"}
BYTES = {"attribute_bytes"}


def literal(column, value):
    if value is None:
        return "NULL"
    if column in TIMESTAMPS:
        return f"timestamptz 'epoch' + {int(value)} * interval '1 millisecond'"
    if column in BOOLEANS:
        return "true" if value else "false"
    if column in BYTES:
        return f"'\\x{bytes(value).hex()}'::bytea"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def main(paths):
    print("BEGIN;")
    for path in paths:
        db = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
        print(f"-- {path}")
        for table in TABLES:
            cursor = db.execute(f'SELECT * FROM "{table}"')
            columns = [d[0] for d in cursor.description]
            rows = cursor.fetchall()
            print(f"-- {table}: {len(rows)} rows")
            for row in rows:
                values = ", ".join(literal(c, v) for c, v in zip(columns, row))
                print(f"INSERT INTO {table} ({', '.join(columns)}) VALUES ({values});")
        db.close()
    # the inserts gave ids explicitly; new accounts continue after the highest
    print("SELECT setval(pg_get_serial_sequence('user_account', 'id'), coalesce(max(id), 0) + 1, false) FROM user_account;")
    print("COMMIT;")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(sys.argv[1:])
