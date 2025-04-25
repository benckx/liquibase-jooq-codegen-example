package dev.encelade.example

import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jooq.DSLContext
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import java.sql.Connection
import java.sql.DriverManager

object DaoService {

    /**
     * Create SQLite connection and update the database schema using Liquibase.
     * The SQLite database file is specified by the dbFileName parameter and is created if it does not exist.
     */
    fun getDslContext(dbFileName: String): DSLContext {
        val conn = DriverManager.getConnection("jdbc:sqlite:$dbFileName")
        updateLiquibase(conn)

        val settings = Settings().withRenderSchema(false)
        return DSL.using(conn, settings)
    }

    private fun updateLiquibase(conn: Connection) {
        val db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(conn))
        val liquibase = Liquibase("liquibase-changelog.xml", ClassLoaderResourceAccessor(), db)
        liquibase.update(Contexts())
    }

}
