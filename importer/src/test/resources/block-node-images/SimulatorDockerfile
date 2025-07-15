FROM eclipse-temurin:21

# Create a non-root user and group
ARG UNAME=hedera
ARG UID=2000
ARG GID=2000
ARG BN_WORKDIR="/opt/hiero/block-node"
ENV BN_WORKDIR=${BN_WORKDIR}
RUN groupadd --gid $GID $UNAME
RUN useradd --no-user-group --create-home --uid $UID --gid $GID --shell /bin/bash hedera
WORKDIR ${BN_WORKDIR}

# Copy the distribution and resources
COPY simulator-*.tar ./simulator.tar

RUN mkdir -p ${BN_WORKDIR}/logs/config
COPY logging.properties ${BN_WORKDIR}/logs/config/logging.properties

# Extract the distribution and block data
RUN tar -xf simulator.tar && \
    rm simulator.tar && \
    cd ${BN_WORKDIR} && \
    chown -R $UID:$GID ${BN_WORKDIR}

# Switch to non-root user
USER $UNAME

# Run the simulator using the extracted directory name
RUN SIMULATOR_DIR=$(ls -d simulator-*/) && \
    echo "#!/bin/bash\n${BN_WORKDIR}/${SIMULATOR_DIR}bin/simulator" > ${BN_WORKDIR}/start.sh && \
    chmod +x ${BN_WORKDIR}/start.sh

ENTRYPOINT ["sh","-c","${BN_WORKDIR}/start.sh"]
