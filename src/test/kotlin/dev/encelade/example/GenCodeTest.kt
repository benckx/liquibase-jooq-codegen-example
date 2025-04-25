package dev.encelade.example

import dev.encelade.example.DaoService.getDslContext
import dev.encelade.example.dao.codegen.tables.daos.PersonDao
import dev.encelade.example.dao.codegen.tables.pojos.Person
import org.junit.jupiter.api.Test

class GenCodeTest {

    private val dslContext = getDslContext("example.db")
    private val readOnlyDao = PersonDao(dslContext.configuration())

    @Test
    fun `the generated code can be used to insert records`() {
        val before = readOnlyDao.count()

        val person = Person()
        person.firstName = "Charles"
        person.lastName = "Baudelaire"
        insert(person)

        val after = readOnlyDao.count()

        println("before insert: $before, after insert: $after")
        assert(after == before + 1) { "Expected count to be ${before + 1}, but was $after" }
    }

    private fun insert(person: Person) {
        dslContext.transaction { cfg ->
            val personDao = PersonDao(cfg)
            personDao.insert(person)
        }
    }

}
