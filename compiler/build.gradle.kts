plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("dev.plaing.compiler.cli.MainKt")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.xerial:sqlite-jdbc:3.45.1.0")
}

tasks.test {
    useJUnitPlatform()
}
