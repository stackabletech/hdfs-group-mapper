FROM docker.stackable.tech/stackable/hadoop:3.3.6-stackable0.0.0-dev

COPY --chown=stackable:stackable ./group-mapper-0.1.0-SNAPSHOT.jar /stackable/hadoop/share/hadoop/tools/lib/