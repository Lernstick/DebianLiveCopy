FROM debian:bullseye-slim

RUN set -eux; \
	apt-get update; \
	apt-get install -y --no-install-recommends \
		ant \
		checkstyle \
		git \
		gradle \
		libdbus-java \
		libmatthew-debug-java \
		libunixsocket-java \
		openjdk-11-jdk \
		openjfx \
	; \
	rm -rf /var/lib/apt/lists/*
