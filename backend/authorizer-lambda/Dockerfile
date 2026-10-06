FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM alpine:latest
WORKDIR /app
COPY --from=builder /app/target/authorizer-lambda-1.0.0.jar /app/function.jar
CMD ["cat"]
