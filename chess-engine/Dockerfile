# The chess engine's image, built by `mvn package` in this module (the build args come from the poms).
#
# It runs on GraalVM CE, the same release as the GraalJS library the engine uses (graaljs.version in the
# root pom). GraalJS only compiles JavaScript on a GraalVM JDK whose JVMCI matches it; on any other JDK
# it interprets, and every move is far slower. The official images (ghcr.io/graalvm/jdk-community) lag
# behind the releases, so the JDK comes from the release download, and jlink cuts it down to the modules
# the engine uses plus the Graal compiler. Only the last stage changes from one build to the next; the
# GraalVM stages stay cached.

FROM debian:bookworm-slim AS graalvm-download
ARG GRAALVM_RELEASE
ARG GRAALVM_ARCHIVE
ARG TARGETARCH
# binutils: objcopy, which jlink --strip-debug uses on the native libraries
RUN apt-get update && apt-get install -y --no-install-recommends binutils ca-certificates curl && rm -rf /var/lib/apt/lists/*
RUN set -eu; \
    case "${TARGETARCH:-amd64}" in \
        amd64) arch=x64 ;; \
        arm64) arch=aarch64 ;; \
        *) echo "unsupported architecture: $TARGETARCH" >&2; exit 1 ;; \
    esac; \
    url="https://github.com/graalvm/graalvm-ce-builds/releases/download/${GRAALVM_RELEASE}/${GRAALVM_ARCHIVE}_linux-${arch}_bin.tar.gz"; \
    curl -fsSL -o graalvm.tar.gz "$url"; \
    echo "$(curl -fsSL "$url.sha256")  graalvm.tar.gz" | sha256sum -c -; \
    mkdir /graalvm; \
    tar -xzf graalvm.tar.gz -C /graalvm --strip-components=1

# The runtime: the JDK modules Spring, ActiveMQ and GraalJS use, and the compiler GraalJS needs (JVMCI,
# the Graal compiler and its Truffle bridge). Add a module here if the engine fails with a
# NoClassDefFoundError for a java.* or jdk.* class.
FROM graalvm-download AS graalvm
RUN /graalvm/bin/jlink \
        --add-modules java.base,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.scripting,java.security.jgss,java.sql,java.transaction.xa,java.xml,jdk.crypto.cryptoki,jdk.management,jdk.naming.dns,jdk.unsupported,jdk.internal.vm.ci,jdk.graal.compiler,jdk.graal.compiler.management,org.graalvm.truffle.compiler \
        --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
        --output /opt/graalvm

FROM debian:bookworm-slim
ARG JAR
COPY --from=graalvm /opt/graalvm /opt/graalvm
ENV JAVA_HOME=/opt/graalvm
ENV PATH=/opt/graalvm/bin:$PATH
# with a home: GraalJS unpacks native libraries to ~/.cache, and without them it can't use the JIT compiler
RUN useradd --system --create-home chess
COPY ${JAR} /app/chess-engine.jar
USER chess
# Truffle loads native code; without --enable-native-access every start warns about it
ENTRYPOINT ["java", "-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI", "--enable-native-access=ALL-UNNAMED", \
            "-jar", "/app/chess-engine.jar"]
