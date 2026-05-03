# 1. Use the full Gradle image to build (No script needed!)
FROM gradle:8.7-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
# Use 'gradle' instead of './gradlew'
RUN gradle bootJar -x test --no-daemon

# 2. Use a tiny JRE to run the app
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar

# 3. The Memory Shield: Keep it small so Render doesn't kill it
ENTRYPOINT ["java", "-Xmx300M", "-Xss512k", "-jar", "app.jar"]