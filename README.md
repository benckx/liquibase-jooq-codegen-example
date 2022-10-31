<a href="https://paypal.me/benckx/2">
<img src="https://img.shields.io/badge/Donate-PayPal-green.svg"/>
</a>

# About

The following tutorial explains how to generate DAO code based on a Liquibase definition. This generated code can be
used e.g. to insert a new row in a table:

```kotlin
dslContext.transaction { cfg ->
    val personDao = PersonDao(cfg)
    val person = Person()
    person.firstName = "Charles"
    person.lastName = "Baudelaire"
    personDao.insert(person)
}
```

Classes `PersonDao` and `Person` have bene generated during the Gradle build, directly from the Liquibase definition.
This reduces the boilerplate of writing DAO code and SQL queries in your application.

# Technical stack

We will use SQLite for the sake of simplicity, but the same approach would work for other DB engines like MySQL or
Postgresql. SQLite is a light-weight DB engine which stores the entire database into a file - which is quite useful for
small systems like e.g. a podcasts manager app running on a phone, that must store information about what podcasts you
are subscribed to and which episodes you have already listened to.

* JDK 18
* Kotlin
* SQLite
* Liquibase
* jOOQ

# Code Generation

Create the Liquibase definition `liquibase-changelog.xml`:

```xml

<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.4.xsd">

    <changeSet author="benckx" id="0001">
        <createTable tableName="person">
            <column name="id" type="int" autoIncrement="true">
                <constraints primaryKey="true"/>
            </column>
            <column name="first_name" type="varchar(255)"/>
            <column name="last_name" type="varchar(255)"/>
        </createTable>
    </changeSet>
</databaseChangeLog>
```

In the Gradle build, add a task that runs before Kotlin compilation:

```groovy
tasks.getByPath("compileKotlin").doFirst {
    // code generation is configured inside this task
}
```

Inside this task, we create an H2 database:

```groovy
import java.sql.Connection
import java.sql.Statement

// [...]

Connection conn = new Driver().connect("jdbc:h2:mem:test", null)

Statement stmt = conn.createStatement()
stmt.execute("drop all OBJECTS")
stmt.execute("create schema EXAMPLE_DB")
stmt.execute("set schema EXAMPLE_DB")
stmt.close()
```

We then run the Liquibase on this database:

```groovy

import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.resource.CompositeResourceAccessor
import liquibase.resource.FileSystemResourceAccessor
import org.h2.Driver

// [...]

def liquibaseDb = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn))
def contextClassLoader = Thread.currentThread().getContextClassLoader()

def threadClFO = new ClassLoaderResourceAccessor(contextClassLoader)
def clFO = new ClassLoaderResourceAccessor()
def fsFO = new FileSystemResourceAccessor()
def liquibase = new Liquibase("src/main/resources/liquibase-changelog.xml", new CompositeResourceAccessor(clFO, fsFO, threadClFO), liquibaseDb)
liquibase.update(new Contexts())
conn.commit()
```

At this point, we have an H2 in-memory database containing our Liquibase definition (i.e. 1 table named `person`).

We then connect to this H2 database with jOOQ to generate the DAO code:

```groovy
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.*

// [...]

GenerationTool.generate(
        new Configuration()
                .withJdbc(new Jdbc()
                        .withDriver('org.h2.Driver')
                        .withUrl('jdbc:h2:mem:test')
                        .withUser('')
                        .withPassword(''))
                .withGenerator(new Generator()
                        .withDatabase(
                                // exclude Liquibase-specific tables
                                new Database()
                                        .withExcludes("DATABASECHANGELOG|DATABASECHANGELOGLOCK")
                                        .withInputSchema("EXAMPLE_DB")
                        )
                        .withGenerate(new Generate()
                                .withPojos(true)
                                .withDaos(true))
                        .withTarget(
                                // specify the target package and directory
                                // by using the build folder, we ensure the generated code is removed on "clean" 
                                // and is not versioned on Git
                                new Target()
                                        .withPackageName('dev.encelade.example.dao.codegen')
                                        .withDirectory("$buildDir/jooq"))
                )
)
```

Finally, we need to add this new generated folder as a source set, so Gradle knows to compile it along our application
code:

```groovy
sourceSets {
    main {
        java {
            srcDirs "$buildDir/jooq"
        }
    }
}
```

Run the Gradle build with `./gradlew clean build`. The new folder will appear at `/build/jooq`.

# Use the generated DAO code

We first need some logic to create and access the SQLite database. If file `dbFileName` doesn't exist, it will be
created automatically. We will also run `updateLiquibase()` to apply any change made to the Liquibase definition
into the SQLite file.

```kotlin
package dev.encelade.example

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jooq.DSLContext
import org.jooq.conf.RenderQuotedNames
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import java.sql.Connection
import java.sql.DriverManager

object DaoService {

    fun getDslContext(dbFileName: String): DSLContext {
        val conn = DriverManager.getConnection("jdbc:sqlite:$dbFileName")
        updateLiquibase(conn)

        val settings = Settings()
            .withRenderSchema(false)
            .withRenderQuotedNames(RenderQuotedNames.NEVER)

        return DSL.using(conn, settings)
    }

    private fun updateLiquibase(conn: Connection) {
        val db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(conn))
        val liquibase = Liquibase("liquibase-changelog.xml", ClassLoaderResourceAccessor(), db)
        liquibase.update(Contexts(), LabelExpression())
    }

}
```

The `DSLContext` is the jOOQ object you need to do any operation to the database. For example, we can use it to insert
a new entry in the table `person`:

```kotlin
package dev.encelade.example

import dev.encelade.example.dao.codegen.tables.daos.PersonDao
import dev.encelade.example.dao.codegen.tables.pojos.Person

fun main() {
    val dslContext = DaoService.getDslContext("example.db")

    dslContext.transaction { cfg ->
        val personDao = PersonDao(cfg)
        val person = Person()
        person.firstName = "Charles"
        person.lastName = "Baudelaire"
        personDao.insert(person)
    }

    dslContext.transaction { cfg ->
        val personDao = PersonDao(cfg)
        println("entries: ${personDao.count()}")
    }
}
```

When running the above, it should print the following (which increases by +1 every time):

```
entries: 1
```

If you open example.db with a DB client, you can see the new entry:

<img src="/img/example.db.png" title="Content of table 'person'">

If you later modify the Liquibase definition, for example by adding new tables, simply run `./gradlew clean build` to
re-generate the DAO code.

# How To

To run it locally:

* `./gradlew clean build` to generate the jOOQ DAO code
* Run the main class

# TODO

There are a few things I would still like to improve about this tutorial:

* Upgrade Liquibase
* Upgrade logging libs
* Add date of birth to Person table
