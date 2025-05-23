# Use the official Microsoft Java 21 dev container image as a base
FROM mcr.microsoft.com/devcontainers/java:1-21-bullseye

# Arguments for Maven version - can be overridden in devcontainer.json
ARG MAVEN_VERSION=3.9.6
ARG USER_HOME_DIR=/home/vscode # Default for vscode user in Microsoft devcontainer images
ARG SHA=6e9da326bd371b26a4a25693a62996309a81897aac0a5390c43e994d55018d8817d458971401b071779096910070700218b05085c900e6803631637177100bf3 # SHA512 for Maven 3.9.6 binary tar.gz

# Install necessary tools like wget, ca-certificates for downloading, and then Maven
USER root
RUN apt-get update && export DEBIAN_FRONTEND=noninteractive \
    && apt-get -y install --no-install-recommends \
        wget \
        ca-certificates \
        # Add any other system dependencies you might need
    # Download and install Maven
    && mkdir -p /usr/share/maven /usr/share/maven/ref \
    && echo "Downloading Maven ${MAVEN_VERSION}" \
    && wget -q -O /tmp/apache-maven.tar.gz "https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
    # Verify checksum (optional but good practice, get SHA from Apache Maven website)
    # && echo "${SHA} */tmp/apache-maven.tar.gz" | sha512sum -c - \
    && tar -xzf /tmp/apache-maven.tar.gz -C /usr/share/maven --strip-components=1 \
    && rm -f /tmp/apache-maven.tar.gz \
    && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn \
    # Clean up apt lists
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*
USER vscode

# Set Maven environment variables
ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG "${USER_HOME_DIR}/.m2"
# Add MAVEN_HOME/bin to PATH (though ln -s should make mvn globally available)
ENV PATH="${MAVEN_HOME}/bin:${PATH}"

# You can add more customizations here, like creating a .m2 directory or settings.xml
# RUN mkdir -p ${USER_HOME_DIR}/.m2