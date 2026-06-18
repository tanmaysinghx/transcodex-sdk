# Use an official Maven image with JDK 25
FROM maven:3.9.6-eclipse-temurin-25-alpine

ARG GITHUB_ACTOR
ARG GITHUB_TOKEN

# 1. Setup credentials file
RUN mkdir -p /root/.m2 && \
    echo "<settings xmlns='http://maven.apache.org/SETTINGS/1.0.0' \
          xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' \
          xsi:schemaLocation='http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd'> \
    <servers> \
        <server> \
            <id>github</id> \
            <username>${GITHUB_ACTOR}</username> \
            <password>${GITHUB_TOKEN}</password> \
        </server> \
    </servers> \
</settings>" > /root/.m2/settings.xml

WORKDIR /app

# 2. Copy the standalone consumer playground project
COPY . /app/

# 3. Clean compiler execution
RUN mvn clean compile
