FROM docker.stackable.tech/stackable/hadoop:3.3.6-stackable0.0.0-dev@sha256:2f23b4cad29748c5268a0635322f56fd939c29025350f9c1ef4b9dbaca1a6cee

COPY --chown=stackable:stackable ./hdfs-utils-*.jar /stackable/hadoop/share/hadoop/tools/lib/
COPY --chown=stackable:stackable ./bom.json /stackable/hadoop/share/hadoop/tools/lib/hdfs-utils.cdx.json
