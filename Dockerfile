FROM docker.stackable.tech/stackable/hadoop:3.4.0-stackable0.0.0-dev@sha256:6ea980e8e42c778122db55b68f16a625c3c88bdd8227a534d67c4a1c21075e56

COPY --chown=stackable:stackable ./hdfs-utils-*.jar /stackable/hadoop/share/hadoop/tools/lib/
COPY --chown=stackable:stackable ./bom.json /stackable/hadoop/share/hadoop/tools/lib/hdfs-utils.cdx.json
