#!/bin/sh
set -e
if [ -z "$GRADLE_USER_HOME" ]; then GRADLE_USER_HOME="$HOME/.gradle"; fi
exec gradle "$@"
