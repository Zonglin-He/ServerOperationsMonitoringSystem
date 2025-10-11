# 构建阶段：使用 Maven 打包 Spring Boot 应用
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

# 运行阶段：仅包含 JRE
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /workspace/target/*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 80
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
