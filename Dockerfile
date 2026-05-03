# 1. Use a very small, lightweight version of Java
FROM eclipse-temurin:17-jdk-alpine as build
COPY . .

# 2. Build the app into a "JAR" file
RUN ./gradlew build -x test

# 3. Create the final "Shipping Container"
FROM eclipse-temurin:17-jre-alpine
COPY --from=build /build/libs/*.jar app.jar

# 4. THE MAGIC LINE: Force the app to stay small so it doesn't crash the free server
ENTRYPOINT ["java","-Xmx400M","-jar","/app.jar"]