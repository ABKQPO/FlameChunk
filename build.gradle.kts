plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

minecraft {
    extraRunJvmArguments.addAll("-Xmx8G", "-Xms8G", "-Dgtnhlib.dumpkeys=true")
}

tasks.withType<JavaCompile>().configureEach {
    options.annotationProcessorPath = configurations.annotationProcessor.get()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

val runConfigs = listOf(
    "runClient" to "run/client",
    "runClient17" to "run/client_new",
    "runClient21" to "run/client_new",
    "runClient25" to "run/client_new",
    "runServer" to "run/server",
    "runServer17" to "run/server_new",
    "runServer21" to "run/server_new",
    "runServer25" to "run/server_new"
)

runConfigs.forEach { (taskName, path) ->
    tasks.named<JavaExec>(taskName) {
        workingDir = file("${projectDir}/$path")
        doFirst {
            workingDir.mkdirs()
        }
    }
}

val mapClientProfiles = mapOf(
    "runClient25Jm5" to ("run/client25_jm5" to "journeyMap5Launch"),
    "runClient25Jm6" to ("run/client25_jm6" to "journeyMap6Launch"),
    "runClient25Xaero" to ("run/client25_xaero" to "xaeroLaunch")
)
val requestedMapClientProfileName = gradle.startParameter.taskNames
    .map { it.substringAfterLast(':') }
    .firstOrNull(mapClientProfiles::containsKey)
val requestedMapClientProfile = requestedMapClientProfileName?.let(mapClientProfiles::get)

if (requestedMapClientProfile != null) {
    tasks.named<JavaExec>("runClient25") {
        classpath = classpath.plus(configurations.getByName(requestedMapClientProfile.second))
        workingDir = file("$projectDir/${requestedMapClientProfile.first}")
        doFirst {
            workingDir.mkdirs()
        }
    }
}

mapClientProfiles.forEach { (taskName, profile) ->
    tasks.register(taskName) {
        group = "application"
        description = "Runs the Java 25 client with ${profile.second}"
        dependsOn(tasks.named("runClient25"))
    }
}
