FROM openjdk:14-jdk-alpine

ENV LAS2PEER_PORT=9011

RUN apk add --update bash curl && rm -f /var/cache/apk/*
RUN addgroup -g 1000 -S las2peer && \
    adduser -u 1000 -S las2peer -G las2peer

# Add files
COPY --chown=las2peer:las2peer . /src

USER las2peer
WORKDIR /src

RUN ./gradlew --version

# Build
RUN chmod +x gradlew && ./gradlew build

EXPOSE $LAS2PEER_PORT

RUN chmod +x /src/docker-entrypoint.sh
ENTRYPOINT ["/src/docker-entrypoint.sh"]
