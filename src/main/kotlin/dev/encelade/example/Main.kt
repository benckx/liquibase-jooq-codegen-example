package dev.encelade.example

import dev.encelade.example.dao.codegen.tables.daos.PersonDao
import dev.encelade.example.dao.codegen.tables.pojos.Person

// TODO: add date of birth to show how date mapping works
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
