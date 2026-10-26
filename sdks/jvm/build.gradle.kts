plugins {
    `java-library`
    `maven-publish`
}

group = "com.heossi"
version = "1.3.0"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    withSourcesJar()
}

repositories { mavenCentral() }

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // The conformance tests invoke the real CLI; pass its location through.
    environment("SAAR_BIN", System.getenv("SAAR_BIN") ?: "")
    testLogging { events("passed", "failed", "skipped") }
}

publishing {
    repositories {
        maven {
            name = "central"
            url = uri("https://central.sonatype.com/api/v1/publisher")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("Saar SDK")
                description.set("Thin JVM client over the Saar command-line interface")
                url.set("https://saar.heossi.com")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
            }
        }
    }
}
