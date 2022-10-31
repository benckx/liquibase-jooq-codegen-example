<a href="https://paypal.me/benckx/2">
<img src="https://img.shields.io/badge/Donate-PayPal-green.svg"/>
</a>

# About

The following tutorial explains how to generate DAO code from your Liquibase definition, that you then can use for
example to insert a new entry in a table:

```kotlin
dslContext.transaction { cfg ->
    val personDao = PersonDao(cfg)
    val person = Person()
    person.firstName = "Charles"
    person.lastName = "Baudelaire"
    personDao.insert(person)
}
```

Classes `PersonDao` and `Person` are generated during the Gradle build, directly from the Liquibase definition. This
reduces the boilerplate of writing DAO code and SQL queries in your application.

During the Gradle build, the Liquibase definition is executed in a H2 in-memory DB and from this DB we run jOOQ codegen
to generated the DAO code.

# Technical stack

* JDK 18
* Kotlin
* SQLite
* Liquibase
* jOOQ

# Code Generation

Create your Liquibase definition `liquibase-changelog.xml`:

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

In the Gradle build, add a task that will run before Kotlin compilation:

```groovy
tasks.getByPath("compileKotlin").doFirst {
    // code generation is configured inside this task
}
```

Inside this task, we create a H2 database:

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

Run the Liquibase in this schema, using the same connection object we used to create the schema:

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

At this point, you have a H2 in-memory database containing your Liquibase definition, in this example it means you have
a database with 1 table.

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
                                // choose the target package and directory
                                // by using build folder, we ensure the generated code is removed on "clean" 
                                // and is not versioned on Git
                                new Target()
                                    .withPackageName('dev.encelade.example.dao.codegen')
                                    .withDirectory("$buildDir/jooq"))
                )
)
```

Finally, we need to add this new generated folder as a source set, so Gradle knows to compile it along with your app
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

We first need some logic to create and access a SQLite database. If  `dbFileName` doesn't exist yet, it will be created
automatically. It will also run `updateLiquibase()` to apply any change you made to your Liquibase definition, into the
SQLite file.

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

The `DSLContext` is the jOOQ object you need to do any operation to your database. For example, we can use it to insert
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

When ran, it should print the following (and increase it by +1 every time it's ran):

```
entries: 1
```

If you open example.db with a DB client, you can see your new entry:

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
