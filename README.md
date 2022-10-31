<a href="https://paypal.me/benckx/2">
<img src="https://img.shields.io/badge/Donate-PayPal-green.svg"/>
</a>

# About

The following example demonstrate how to generate DAO code from your Liquibase definition, that you can use this way,
for example to insert a new entry:

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
reduces the boilerplate of writing DAO code and SQL queries.

During the Gradle build, the Liquibase definition is executed in a H2 in-memory DB, and the jOOQ DAO code is generated
based on this H2 schema. You can then use this generated code in your application.

# Code Generation

Create Liquibase definition `liquibase-changelog.xml`:

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

Inside this task, create a H2 database and schema:

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

At this point, you have a H2 in-memory database with your Liquibase definition, in this example it means you have
created one table.

Then connect to this H2 database with jOOQ to generate the DAO code:

```groovy
import org.h2.Driver
import org.jooq.codegen.GenerationTool
import groovy.xml.MarkupBuilder

// [...]

def writer = new StringWriter()
new MarkupBuilder(writer).configuration('xmlns': 'http://www.jooq.org/xsd/jooq-codegen-3.11.0.xsd') {
    jdbc() {
        driver('org.h2.Driver')
        url("jdbc:h2:mem:test")
        user("")
        password("")
    }
    generator {
        // we exclude Liquibase tables from the generation
        database {
            inputSchema('EXAMPLE_DB')
            excludes('DATABASECHANGELOG|DATABASECHANGELOGLOCK')
        }
        generate([:]) {
            pojos false
            daos true
        }
        // we select the target package (the package your generated objects will belong to)
        // and the folder where the code will be generated; we can use the "build" folder, so it 
        // will be deleted when running Gradle "clean" and it should also be excluded from Git
        target() {
            packageName('be.encelade.example.dao.codegen')
            directory("$buildDir/jooq")
        }
    }
}

GenerationTool.generate(writer.toString())
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

# Use the generated code

We first need some logic to create and access a SQLite database. If  `dbFileName` doesn't exist yet, it will be created
automatically. It will also apply `updateLiquibase()` to apply any change you made to your Liquibase definition to the
SQLite instance.

```kotlin
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jooq.DSLContext
import org.jooq.conf.RenderNameStyle
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
            .withRenderNameStyle(RenderNameStyle.LOWER)

        return DSL.using(conn, settings)
    }

    private fun updateLiquibase(conn: Connection) {
        val db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(conn))
        val liquibase = Liquibase("liquibase-changelog.xml", ClassLoaderResourceAccessor(), db)
        liquibase.update(Contexts(), LabelExpression())
    }

}
```

The `DSLContext` is the jOOQ object you need to do any operation to your database. For example, we can insert a new
entry in the table `person`.

```groovy
import be.encelade.example.dao.codegen.Tables.PERSON
import be.encelade.example.dao.codegen.tables.daos.PersonDao
import be.encelade.example.dao.codegen.tables.pojos.Person

fun main() {
    val dslContext = DaoService.getDslContext("example.db")

    dslContext.transaction { cfg ->
        val personDao = PersonDao(cfg)
        val person = Person()
        person.firstName = "Charles"
        person.lastName = "Baudelaire"
        personDao.insert(person)
    }

    val count = dslContext
            .selectCount()
            .from(PERSON)
            .fetchOneInto(Int::class.java)

    println("entries: $count")
}
```

This should print the following, and increase it by +1 every time you run it.

```
entries: 1
```

If you open example.db, you can see your new entry:

<img src="/img/example.db.png" title="Content of table 'person'">

If you later modify the Liquibase definition, for example by adding new tables, simply run `./gradlew clean build` to
re-generate the DAO code.

# How To

To run it locally:

* `./gradlew clean build` to generate the jOOQ DAO code
* Run the main class

# Technical stack

* Kotlin
* SQLite
* Liquibase
* jOOQ
