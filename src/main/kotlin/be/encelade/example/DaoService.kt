package be.encelade.example

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

class DaoService(dbFileName: String = "example.db") {

    val dslContext = getDslContext(dbFileName)

    companion object {

        private fun getDslContext(dbFileName: String): DSLContext {
            val conn = DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            updateLiquibase(conn)

            val settings = Settings()
                    .withRenderSchema(false)
                    .withRenderNameStyle(RenderNameStyle.LOWER)
                    .withReturnRecordToPojo(false) // enable batch inserts

            return DSL.using(conn, settings)
        }

        private fun updateLiquibase(conn: Connection) {
            val db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(conn))
            val liquibase = Liquibase("liquibase-changelog.xml", ClassLoaderResourceAccessor(), db)
            liquibase.update(Contexts(), LabelExpression())
        }
    }
}
