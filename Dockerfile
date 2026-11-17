# Local Run Tests image (Exa Studio builds this; the server grader does not).
# Warms ~/.m2 from pom.xml + a throwaway test so `mvn -o test` runs offline.
# Never copies src/, so it builds whatever state the learner's code is in.
FROM maven:3.9.9-eclipse-temurin-17
# This lab only: Task 1's test runs the learner's seed.sql with the real psql
# client, so the image needs it (PSQL can point the tests at another path).
RUN apt-get update \
 && apt-get install -y --no-install-recommends postgresql-client \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /warm
COPY pom.xml ./
RUN mkdir -p src/test/java/warm \
 && printf 'package warm;\nclass WarmTest { @org.junit.jupiter.api.Test void ok() {} }\n' \
      > src/test/java/warm/WarmTest.java \
 && mvn -B -q test \
 && rm -rf /warm
WORKDIR /work
