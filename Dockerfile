# 1. Use a full Gradle image to build (No wrapper script needed!)
FROM gradle:8.7-jdk17-alpine AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle bootJar -x test --no-daemon

# 2. Use a tiny JRE to run the app
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar

# 3. THE LIMIT: Keep it under 350MB for the free server
ENTRYPOINT ["java", "-Xmx350M", "-jar", "app.jar"]