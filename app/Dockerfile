FROM eclipse-temurin:17-jre
WORKDIR /app

# 호스트에서 빌드해둔 jar만 복사
COPY build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

