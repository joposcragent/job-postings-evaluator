import nu.studer.gradle.jooq.JooqEdition
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	id("org.springframework.boot") version "4.0.5"
	id("io.spring.dependency-management") version "1.1.7"
	id("nu.studer.jooq") version "9.0"
	id("org.openapi.generator") version "7.14.0"
	jacoco
}

group = "ru.sadovskie.leo.app.joposcragent"
version = "2.4.0"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.1")
	}
}

val openapiEvaluatorModelsDir = layout.buildDirectory.dir("generated/openapi-evaluator").get().asFile.path
val openapiSettingsFeignDir = layout.buildDirectory.dir("generated/openapi-settings-feign").get().asFile.path
val openapiSentenceFeignDir = layout.buildDirectory.dir("generated/openapi-sentence-feign").get().asFile.path

tasks.register<GenerateTask>("openApiGenerateEvaluatorModels") {
	generatorName.set("kotlin")
	val specFile = layout.projectDirectory.file("../../specifications/services/job-postings-evaluator/openapi.yaml").asFile
	inputSpec.set(specFile.toURI().toString())
	outputDir.set(openapiEvaluatorModelsDir)
	packageName.set("ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.openapi")
	apiPackage.set("ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.openapi.api")
	modelPackage.set("ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.openapi.model")
	configOptions.set(
		mapOf(
			"serializationLibrary" to "jackson",
			"enumPropertyNaming" to "UPPERCASE",
			"useSpringBoot3" to "true",
			"documentationProvider" to "none",
		),
	)
	globalProperties.set(
		mapOf(
			"models" to "",
			"modelDocs" to "false",
			"apis" to "false",
			"supportingFiles" to "false",
		),
	)
}

tasks.register<GenerateTask>("openApiGenerateSettingsFeign") {
	generatorName.set("spring")
	val specFile = layout.projectDirectory.file("src/openapi/settings-manager-evaluator.yaml").asFile
	inputSpec.set(specFile.toURI().toString())
	outputDir.set(openapiSettingsFeignDir)
	packageName.set("ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.settings")
	apiPackage.set("ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.settings.api")
	modelPackage.set("ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.settings.model")
	configOptions.set(
		mapOf(
			"library" to "spring-cloud",
			"useSpringBoot3" to "true",
			"documentationProvider" to "none",
			"dateLibrary" to "java8",
			"openApiNullable" to "false",
			"interfaceOnly" to "true",
			"skipDefaultInterface" to "true",
		),
	)
	globalProperties.set(
		mapOf(
			"models" to "",
			"modelDocs" to "false",
			"apis" to "",
		),
	)
}

tasks.register<GenerateTask>("openApiGenerateSentenceFeign") {
	generatorName.set("spring")
	val specFile = layout.projectDirectory.file("src/openapi/sentence-transformer-evaluator.yaml").asFile
	inputSpec.set(specFile.toURI().toString())
	outputDir.set(openapiSentenceFeignDir)
	packageName.set("ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.sentence")
	apiPackage.set("ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.sentence.api")
	modelPackage.set("ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.sentence.model")
	configOptions.set(
		mapOf(
			"library" to "spring-cloud",
			"useSpringBoot3" to "true",
			"documentationProvider" to "none",
			"dateLibrary" to "java8",
			"openApiNullable" to "false",
			"interfaceOnly" to "true",
			"skipDefaultInterface" to "true",
		),
	)
	globalProperties.set(
		mapOf(
			"models" to "",
			"modelDocs" to "false",
			"apis" to "",
		),
	)
}

dependencies {
	implementation("net.logstash.logback:logstash-logback-encoder:8.1")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-jooq")
	implementation("org.springframework.boot:spring-boot-starter-kafka")
	implementation("org.postgresql:postgresql")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
	implementation("io.github.openfeign:feign-okhttp")
	implementation("io.swagger.core.v3:swagger-annotations:2.2.22")

	jooqGenerator("org.postgresql:postgresql")

	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-jooq-test")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.kafka:spring-kafka-test")
	testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("io.mockk:mockk-jvm:1.14.6")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:postgresql:1.20.4")
	testImplementation("org.testcontainers:kafka:1.20.4")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

sourceSets["main"].kotlin.srcDir("$openapiEvaluatorModelsDir/src/main/kotlin")
sourceSets["main"].java.srcDir("$openapiSettingsFeignDir/src/main/java")
sourceSets["main"].java.srcDir("$openapiSentenceFeignDir/src/main/java")
sourceSets["main"].java.srcDir("build/generated-src/jooq/main")

val jooqDbUrl = System.getenv("JOOQ_DB_URL") ?: "jdbc:postgresql://localhost:5432/joposcragent"
val jooqDbUser = System.getenv("JOOQ_DB_USER") ?: "postgres"
val jooqDbPassword = System.getenv("JOOQ_DB_PASSWORD") ?: "postgres"

jooq {
	version.set("3.19.31")
	edition.set(JooqEdition.OSS)
	configurations {
		create("main") {
			generateSchemaSourceOnCompilation.set(false)
			jooqConfiguration.apply {
				jdbc = Jdbc().apply {
					driver = "org.postgresql.Driver"
					url = jooqDbUrl
					user = jooqDbUser
					password = jooqDbPassword
				}
				generator = Generator().apply {
					database = Database().apply {
						name = "org.jooq.meta.postgres.PostgresDatabase"
						inputSchema = "job_postings"
						excludes = "flyway_schema_history"
					}
					generate = Generate().apply {
						isDeprecated = false
						isRecords = true
						isImmutablePojos = true
						isFluentSetters = true
					}
					target = Target().apply {
						packageName = "ru.sadovskie.leo.app.joposcragent.jobpostings.jooq"
						directory = "build/generated-src/jooq/main"
					}
				}
			}
		}
	}
}

tasks.named("compileJava") {
	dependsOn("openApiGenerateSettingsFeign", "openApiGenerateSentenceFeign")
}

tasks.named("compileKotlin") {
	dependsOn(
		"openApiGenerateEvaluatorModels",
		"generateJooq",
		"openApiGenerateSettingsFeign",
		"openApiGenerateSentenceFeign",
	)
}

val dockerImageRepository = System.getenv("IMAGE_NAME") ?: "joposcragent/${rootProject.name}"
val dockerImageTag = System.getenv("IMAGE_TAG") ?: project.version.toString()

tasks.bootBuildImage {
	imageName.set("$dockerImageRepository:$dockerImageTag")
	finalizedBy("bootBuildImageTagLatest")
}

tasks.register<Exec>("bootBuildImageTagLatest") {
	group = "container"
	description = "docker tag: помечает образ из bootBuildImage тегом latest"
	commandLine(
		"docker", "tag",
		"$dockerImageRepository:$dockerImageTag",
		"$dockerImageRepository:latest",
	)
}

tasks.withType<Test> {
	useJUnitPlatform()
	finalizedBy(tasks.jacocoTestReport)
}

val jacocoInstrumentedClasses = files(
	sourceSets["main"].output.classesDirs.files.map { dir ->
		fileTree(dir) {
			exclude("**/jobpostings/jooq/**")
			exclude("**/jobpostingsevaluator/**")
		}
	},
)

tasks.named<JacocoReport>("jacocoTestReport") {
	dependsOn(tasks.test)
	classDirectories.setFrom(jacocoInstrumentedClasses)
	reports {
		xml.required.set(true)
		html.required.set(true)
	}
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
	dependsOn(tasks.jacocoTestReport)
	classDirectories.setFrom(jacocoInstrumentedClasses)
	violationRules {
		rule {
			limit {
				counter = "LINE"
				minimum = "0.60".toBigDecimal()
			}
		}
	}
}

tasks.check {
	dependsOn(tasks.jacocoTestCoverageVerification)
}
