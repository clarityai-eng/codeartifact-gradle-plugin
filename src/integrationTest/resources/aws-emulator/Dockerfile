# AWS Emulator (https://github.com/aws/aws-emulator) for the integration tests.
#
# AWS publishes no prebuilt image, only this Dockerfile shape, and no versioned package:
# LTS/latest is the only download URL. The tests log the version that served them.
FROM public.ecr.aws/amazonlinux/amazonlinux:2023-minimal

# x86_64 or aarch64, chosen by the tests from the architecture of the JVM running them
ARG EMULATOR_ARCH=x86_64

ADD https://downloads.aws.com/aws-emulator/packages/aws-emulator/LTS/latest/aws-emulator-${EMULATOR_ARCH}.rpm /tmp/aws-emulator.rpm
RUN rpm -i /tmp/aws-emulator.rpm && rm /tmp/aws-emulator.rpm \
    && mkdir -p /home/emulator && chown 1000 /home/emulator

USER 1000
ENV HOME=/home/emulator
EXPOSE 9297

# Listens on every address inside the container so that Docker can forward the port
ENTRYPOINT ["aws-emulator", "-b", "0.0.0.0"]
