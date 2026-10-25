# GraphHopper: silnik wyznaczania tras (rower, bieg/spacer) na danych OpenStreetMap.
# Dane (plik .osm.pbf i wyliczony graf) leżą w folderze routing/data, poza obrazem.
FROM eclipse-temurin:21-jre

ARG GH_VERSION=11.0
ADD https://github.com/graphhopper/graphhopper/releases/download/${GH_VERSION}/graphhopper-web-${GH_VERSION}.jar /opt/graphhopper/graphhopper-web.jar
COPY config.yml /opt/graphhopper/config.yml

ENV OSM_PBF=lubelskie-latest.osm.pbf
ENV JAVA_OPTS="-Xms512m -Xmx2g"

VOLUME /data
EXPOSE 8989

# sh -c, żeby nazwę pliku i pamięć dało się zmieniać zmiennymi środowiskowymi
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Ddw.graphhopper.datareader.file=/data/$OSM_PBF -Ddw.graphhopper.graph.location=/data/graph-cache -jar /opt/graphhopper/graphhopper-web.jar server /opt/graphhopper/config.yml"]
