plugins {
    id("org.springframework.boot")
}

group = "ru.team22.musicalgreetings"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}