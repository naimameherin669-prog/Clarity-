plugins { id("org.springframework.boot") version "3.2.5"; id("io.spring.dependency-management") version "1.1.4"; kotlin("jvm") version "1.9.23"; kotlin("plugin.spring") version "1.9.23"; kotlin("plugin.jpa") version "1.9.23" }
repositories { mavenCentral() }
dependencies {implementation("org.springframework.boot:spring-boot-starter-undertow")}
