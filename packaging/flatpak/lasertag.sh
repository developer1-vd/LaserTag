#!/bin/sh
# Flatpak launcher. Java's preferences (name, settings) go to the app's own config folder,
# since the sandbox can't write to ~/.java.
exec /app/jre/bin/java \
  -Djava.util.prefs.userRoot="${XDG_CONFIG_HOME}/java" \
  -Djava.util.prefs.systemRoot="${XDG_CONFIG_HOME}/java-system" \
  -Dawt.useSystemAAFontSettings=on \
  -jar /app/lib/lasertag/lasertag.jar "$@"
