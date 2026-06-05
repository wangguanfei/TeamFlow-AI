FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
ENV TZ=Asia/Shanghai \
    JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Shanghai
WORKDIR /app
COPY --from=build /app/target/teamflow-ai-backend-0.1.0-SNAPSHOT.jar /app/teamflow-ai-backend.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/teamflow-ai-backend.jar"]
