#!/bin/sh
# Opt-in. Does nothing unless you run it.
# На проде только после мержа, с бэкапом оригиналов; этот агент его не запускал.
#
# Walks a media root. Default is --dry-run (prints the plan, writes nothing).
# --apply copies each original into a sibling media-backup-<timestamp>/ directory
# before overwrite and never deletes that backup. Gallery is skipped.
# Do not point this at production from a build VM.
#
#   deploy/media-orient-backup.sh /path/to/media
#   deploy/media-orient-backup.sh --apply /path/to/media
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
cd "$ROOT/backend"
./mvnw -q -DskipTests compile
exec java -cp target/classes com.studentleague.storage.MediaOrientBackup "$@"
