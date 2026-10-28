# SH1249 — the four variants the spike compares, three cold runs each, one after another so
# no two share the CPU. Run from the repository root once both spike images are built.
$ErrorActionPreference = "Stop"
$m = "scripts/native-spike/measure.mjs"
$serial = "-XX:+UseSerialGC -Xms64m -Xmx512m"
node $m jvm-shipped shio-jvm:spike
node $m jvm-serialgc shio-jvm:spike --env "JAVA_OPTS=$serial"
node $m jvm-cds-serialgc shio-jvm:spike --env "JAVA_OPTS=-XX:SharedArchiveFile=/app/cds/app.jsa $serial" --env "JAR=/app/cds/app.jar"
node $m native shio-native:spike
