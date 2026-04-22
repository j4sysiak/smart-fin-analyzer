package tools

import org.flywaydb.core.Flyway

class RunFlyway {
    static void main(String[] args) {
        String url = System.getenv('FLYWAY_URL')
        String user = System.getenv('FLYWAY_USER')
        String password = System.getenv('FLYWAY_PASSWORD')
        if (!url) {
            println "Missing FLYWAY_URL env var"
            System.exit(2)
        }

        println "Running Flyway migrate on ${url} (user=${user})"

        Flyway flyway = Flyway.configure()
                .dataSource(url, user ?: null, password ?: null)
                .baselineOnMigrate(true)
                .locations("filesystem:src/main/resources/db/migration")
                .load()

        def result = flyway.migrate()
        println "Migrations applied: ${result.migrationsExecuted}"

        def info = flyway.info()
        info.applied().each { mi ->
            println "${mi.version} \t ${mi.description} \t ${mi.state}"
        }

        System.exit(0)
    }
}

